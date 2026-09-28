import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;

/** Operator probe only; no domain events or production bootstrap side effects. */
class KafkaProbe {
    static final String ADDRESS = "127.0.0.1:19092";
    static final String TOPIC = "sahha.synthetic.probe.v1";
    static final List<String> TOPICS = List.of(TOPIC, "sahha.auth.security-events.v1", "sahha.organisation.events.v1",
        "sahha.patient.events.v1", "sahha.scheduling.appointments.v1", "sahha.clinical.consultations.v1",
        "sahha.communication.messages.v1", "sahha.communication.referrals.v1", "sahha.file.medical-files.v1");
    public static void main(String[] args) {
        try {
            if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{22}")) throw new IllegalArgumentException();
            try (Admin admin = Admin.create(Map.of("bootstrap.servers", ADDRESS, "default.api.timeout.ms", "10000", "request.timeout.ms", "5000"))) {
                if (!args[0].equals(admin.describeCluster().clusterId().get(10,TimeUnit.SECONDS))) throw new IllegalStateException();
                for (String topic : TOPICS) {
                    try { admin.createTopics(List.of(new NewTopic(topic,1,(short)1))).all().get(10,TimeUnit.SECONDS); }
                    catch (java.util.concurrent.ExecutionException exists) { if (!(exists.getCause() instanceof TopicExistsException)) throw exists; }
                    var description = admin.describeTopics(List.of(topic)).allTopicNames().get(10,TimeUnit.SECONDS).get(topic);
                    if (description.partitions().size() != 1 || description.partitions().getFirst().replicas().size() != 1) throw new IllegalStateException();
                }
            }
            String key = UUID.randomUUID().toString(), value = "synthetic-probe-only";
            Properties producer = new Properties();
            producer.putAll(Map.of("bootstrap.servers",ADDRESS,"key.serializer","org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer","org.apache.kafka.common.serialization.StringSerializer","acks","all","enable.idempotence","true",
                "delivery.timeout.ms","15000","request.timeout.ms","5000","max.block.ms","15000"));
            long offset;
            try (KafkaProducer<String,String> sender = new KafkaProducer<>(producer)) {
                offset = sender.send(new ProducerRecord<>(TOPIC,key,value)).get(20,TimeUnit.SECONDS).offset();
            }
            Properties consumer = new Properties();
            consumer.putAll(Map.of("bootstrap.servers",ADDRESS,"key.deserializer","org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer","org.apache.kafka.common.serialization.StringDeserializer","enable.auto.commit","false",
                "allow.auto.create.topics","false","default.api.timeout.ms","15000"));
            boolean found = false;
            try (KafkaConsumer<String,String> reader = new KafkaConsumer<>(consumer)) {
                TopicPartition partition = new TopicPartition(TOPIC,0);
                reader.assign(List.of(partition)); reader.seek(partition,offset);
                long deadline = System.nanoTime()+Duration.ofSeconds(15).toNanos();
                while (!found && System.nanoTime() < deadline) {
                    for (var record : reader.poll(Duration.ofSeconds(1))) {
                        if (record.offset() == offset && key.equals(record.key()) && value.equals(record.value())) found = true;
                    }
                }
            }
            if (!found) throw new IllegalStateException();
            System.out.println("SYNTHETIC_KAFKA_OK topics=9 acknowledged-roundtrip=true");
        } catch (Exception failure) {
            System.err.println("Synthetic Kafka probe refused or failed; raw diagnostics suppressed."); System.exit(1);
        }
    }
}
