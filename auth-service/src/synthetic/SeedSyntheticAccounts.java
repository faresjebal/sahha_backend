import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Auth-owned offline synthetic seed. Never compiled into the production JAR. */
class SeedSyntheticAccounts {
    static final String[] KEYS = {"platform", "orgAdmin", "otherAdmin", "doctorA", "doctorB", "unrelatedDoctor", "receptionist", "patient"};
    static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder(12);
    static final UUID PLATFORM_ROLE = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    public static void main(String[] args) {
        try {
            if (args.length != 3 || !args[0].matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) throw new IllegalArgumentException();
            Properties db = properties(args[1]), accounts = properties(args[2]);
            if (!"jdbc:postgresql://127.0.0.1:15432/sahha_demo_auth".equals(db.getProperty("spring.datasource.url"))
                    || !"sahha_demo_auth_app".equals(db.getProperty("spring.datasource.username"))
                    || !args[0].equals(db.getProperty("sahha.synthetic.generation"))
                    || !args[0].equals(accounts.getProperty("generation"))) throw new IllegalArgumentException();
            try (Connection c = DriverManager.getConnection(db.getProperty("spring.datasource.url"), db.getProperty("spring.datasource.username"), db.getProperty("spring.datasource.password"))) {
                c.setAutoCommit(false);
                try {
                    try (var q = c.createStatement(); var r = q.executeQuery("SELECT generation::text,service FROM sahha_synthetic.environment")) {
                        if (!r.next() || !args[0].equals(r.getString(1)) || !"auth".equals(r.getString(2)) || r.next()) throw new IllegalArgumentException();
                    }
                    // Serialise seed invocations even outside the operator CLI lock.
                    try (var q = c.createStatement()) { q.execute("SELECT pg_advisory_xact_lock(728394015)"); }
                    int created = 0;
                    for (int index = 0; index < KEYS.length; index++) {
                        String key = KEYS[index], email = key.toLowerCase(java.util.Locale.ROOT) + "@sahha.example.test";
                        UUID id = UUID.fromString(String.format("d1000000-0000-4000-8000-%012d", index + 1));
                        String password = accounts.getProperty(key + ".password", "");
                        if (!password.matches("Demo-Aa1![A-Za-z0-9_-]{32}")) throw new IllegalArgumentException();
                        boolean exists;
                        try (var q = c.prepareStatement("SELECT id,normalized_email,password_hash,status,email_verified_at FROM user_account WHERE id=? OR normalized_email=?")) {
                            q.setObject(1, id); q.setString(2, email);
                            try (var r = q.executeQuery()) {
                                exists = r.next();
                                if (exists && (!id.equals(r.getObject(1, UUID.class)) || !email.equals(r.getString(2))
                                        || !PASSWORDS.matches(password, r.getString(3)) || !"ACTIVE".equals(r.getString(4)) || r.getTimestamp(5) == null || r.next())) {
                                    throw new IllegalStateException("Existing seed differs; never overwrite credentials or lifecycle");
                                }
                            }
                        }
                        if (!exists) {
                            try (var q = c.prepareStatement("INSERT INTO user_account(id,email,normalized_email,password_hash,first_name,last_name,status,email_verified_at) VALUES (?,?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP)")) {
                                q.setObject(1, id); q.setString(2, email); q.setString(3, email); q.setString(4, PASSWORDS.encode(password));
                                q.setString(5, "Synthetic"); q.setString(6, key); q.executeUpdate();
                            }
                            record(c, id, "SYNTHETIC_ACCOUNT_BOOTSTRAPPED", args[0]); created++;
                        }
                        if (index == 0) {
                            boolean assigned;
                            try (var q = c.prepareStatement("SELECT active FROM user_platform_role WHERE user_id=? AND role_id=?")) {
                                q.setObject(1, id); q.setObject(2, PLATFORM_ROLE);
                                try (var r = q.executeQuery()) { assigned = r.next(); if (assigned && !r.getBoolean(1)) throw new IllegalStateException(); }
                            }
                            if (!assigned) {
                                try (var q = c.prepareStatement("INSERT INTO user_platform_role(id,user_id,role_id,assigned_by_user_id) VALUES (?,?,?,?)")) {
                                    q.setObject(1, UUID.randomUUID()); q.setObject(2, id); q.setObject(3, PLATFORM_ROLE); q.setObject(4, id); q.executeUpdate();
                                }
                                record(c, id, "SYNTHETIC_PLATFORM_ROLE_BOOTSTRAPPED", args[0]);
                            }
                        } else {
                            try (var q = c.prepareStatement("SELECT count(*) FROM user_platform_role WHERE user_id=? AND active")) {
                                q.setObject(1, id); try (var r = q.executeQuery()) { r.next(); if (r.getLong(1) != 0) throw new IllegalStateException(); }
                            }
                        }
                    }
                    c.commit();
                    System.out.println("SYNTHETIC_AUTH_OK accounts=8 created=" + created);
                } catch (Exception failure) { c.rollback(); throw failure; }
            }
        } catch (Exception failure) {
            System.err.println("Synthetic Auth seed refused or failed; transaction rolled back, raw diagnostics suppressed."); System.exit(1);
        }
    }
    static Properties properties(String file) throws Exception {
        Properties result = new Properties();
        try (var input = Files.newInputStream(Path.of(file))) { result.load(input); }
        return result;
    }
    static void record(Connection c, UUID user, String type, String generation) throws Exception {
        UUID event = UUID.randomUUID();
        try (var q = c.prepareStatement("INSERT INTO security_event(id,user_id,subject_user_id,event_type,result,reason_code,request_id,occurred_at) VALUES (?,?,?,?,'SUCCESS','ISOLATED_SYNTHETIC_ONLY',?,CURRENT_TIMESTAMP)")) {
            q.setObject(1, event); q.setObject(2, user); q.setObject(3, user); q.setString(4, type); q.setString(5, "synthetic-" + generation); q.executeUpdate();
        }
        // Metadata-only outbox intent; never publish passwords, email or clinical data.
        try (var q = c.prepareStatement("INSERT INTO auth_outbox_event(id,security_event_id,event_type,payload,occurred_at,next_attempt_at) SELECT ?,id,event_type,jsonb_build_object('eventId',id,'userId',user_id,'subjectUserId',subject_user_id,'eventType',event_type,'result',result,'reasonCode',reason_code,'requestId',request_id,'occurredAt',occurred_at),occurred_at,occurred_at FROM security_event WHERE id=?")) {
            q.setObject(1, UUID.randomUUID()); q.setObject(2, event); q.executeUpdate();
        }
    }
}
