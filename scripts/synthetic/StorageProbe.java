import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

/** Pinned File SDK, fixed isolated endpoint; never reads application credentials. */
class StorageProbe {
    static final String ENDPOINT = "http://127.0.0.1:18333", BUCKET = "sahha-synthetic-files";
    static S3Client client(String access, String secret) {
        return S3Client.builder().endpointOverride(URI.create(ENDPOINT)).region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(15)).apiCallAttemptTimeout(Duration.ofSeconds(5))).build();
    }
    public static void main(String[] args) {
        try {
            if (args.length != 2 || !UUID.fromString(args[0]).toString().equals(args[0])) throw new IllegalArgumentException();
            Properties config = new Properties();
            try (var input = Files.newInputStream(Path.of(args[1]))) { config.load(input); }
            if (!args[0].equals(config.getProperty("generation")) || !ENDPOINT.equals(config.getProperty("endpoint"))
                || !BUCKET.equals(config.getProperty("bucket")) || !config.getProperty("accessKey","").matches("[a-f0-9]{32}")
                || !config.getProperty("secretKey","").matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
            try (S3Client s3 = client(config.getProperty("accessKey"),config.getProperty("secretKey"))) {
                try { s3.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build()); }
                catch (S3Exception missing) {
                    if (missing.statusCode() != 404) throw missing;
                    s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
                }
                String sentinel = "bootstrap/synthetic-generation.txt";
                boolean sentinelCreated = false;
                try {
                    if (!args[0].equals(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(sentinel).build()).asUtf8String())) throw new IllegalStateException();
                } catch (S3Exception missing) {
                    if (missing.statusCode() != 404) throw missing;
                    s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(sentinel).contentType("text/plain").build(),RequestBody.fromString(args[0]));
                    sentinelCreated = true;
                }
                String key = "probe/"+UUID.randomUUID()+".txt", bytes = "synthetic-storage-probe-only";
                try {
                    s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).contentType("text/plain").build(),RequestBody.fromString(bytes));
                    if (!bytes.equals(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(key).build()).asUtf8String())) throw new IllegalStateException();
                    try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                        int anonymous = http.send(HttpRequest.newBuilder(URI.create(ENDPOINT+"/"+BUCKET+"/"+key)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
                        if (anonymous != 403) throw new IllegalStateException();
                    }
                    try (S3Client wrong = client(config.getProperty("accessKey"),"0".repeat(64))) {
                        try { wrong.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()); throw new IllegalStateException(); }
                        catch (S3Exception denied) { if (denied.statusCode() != 403) throw denied; }
                    }
                } finally { s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(key).build()); }
                try { s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()); throw new IllegalStateException(); }
                catch (S3Exception absent) { if (absent.statusCode() != 404) throw absent; }
                System.out.println("SYNTHETIC_STORAGE_SENTINEL_CREATED="+sentinelCreated);
            }
            System.out.println("SYNTHETIC_STORAGE_OK signed-roundtrip=true anonymous-denied=true wrong-key-denied=true generation-preserved=true");
        } catch (Exception failure) {
            System.err.println("Synthetic storage probe refused or failed; raw diagnostics suppressed."); System.exit(1);
        }
    }
}
