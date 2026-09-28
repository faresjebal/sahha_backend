import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.Set;
import org.flywaydb.core.Flyway;

/** Offline operator command, not an application bean, endpoint or shared service. */
class MigrateDatabase {
    public static void main(String[] args) {
        try {
            if (args.length != 4 || !Set.of("auth", "organisation", "patient", "scheduling", "clinical",
                    "communication", "notification", "file", "audit").contains(args[0])
                    || !args[1].matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) {
                throw new IllegalArgumentException("Invalid synthetic migration arguments");
            }
            String service = args[0];
            Properties settings = new Properties();
            try (InputStream input = Files.newInputStream(Path.of(args[2]))) { settings.load(input); }
            String url = settings.getProperty("spring.datasource.url");
            String user = settings.getProperty("spring.datasource.username");
            String password = settings.getProperty("spring.datasource.password");
            if (!("jdbc:postgresql://127.0.0.1:15432/sahha_demo_" + service).equals(url)
                    || !("sahha_demo_" + service + "_app").equals(user)
                    || !args[1].equals(settings.getProperty("sahha.synthetic.generation"))) {
                throw new IllegalArgumentException("Non-synthetic migration target refused");
            }
            Path migrations = Path.of(args[3]).toRealPath();
            Path expected = Path.of(service + "-service", "src", "main", "resources", "db", "migration");
            if (!migrations.endsWith(expected)) throw new IllegalArgumentException("Service migration boundary mismatch");
            try (var connection = DriverManager.getConnection(url, user, password);
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT generation::text, service FROM sahha_synthetic.environment")) {
                if (!result.next() || !args[1].equals(result.getString(1)) || !service.equals(result.getString(2)) || result.next()) {
                    throw new IllegalStateException("Synthetic marker mismatch");
                }
            }
            Flyway flyway = Flyway.configure().dataSource(url, user, password)
                    .locations("filesystem:" + migrations.toString().replace('\\', '/'))
                    .defaultSchema("public").schemas("public").cleanDisabled(true)
                    .baselineOnMigrate(false).validateOnMigrate(true).loggers("slf4j").load();
            flyway.migrate();
            flyway.validate();
            if (flyway.info().pending().length != 0) throw new IllegalStateException("Pending migrations remain");
            System.out.println("MIGRATION_OK " + service + " applied=" + flyway.info().applied().length + " pending=0");
        } catch (Exception failure) {
            // No connection string, SQL, passwords or raw exception diagnostics.
            System.err.println("Synthetic migration failed safely; no clean/baseline/repair was attempted.");
            System.exit(1);
        }
    }
}
