package com.sahha.communication.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Exercise real old data through V1-V4 in a unique, transaction-rolled-back schema. */
@SpringBootTest
@Transactional
class ReferralTypeMigrationIntegrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test void upgradingAnActiveLegacyReferralPreservesSelectionAndNeverCreatesSharedCare() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var start = connection.setSavepoint();
            String schema = "referral_upgrade_" + UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("create schema " + schema);
                statement.execute("set local search_path to " + schema);
                for (String migration : List.of("V1__create_secure_conversation_foundation.sql",
                        "V2__create_referral_sharing_foundation.sql", "V3__record_collaboration_source.sql")) {
                    statement.execute(migration(migration));
                }
                UUID id = UUID.randomUUID(), org = UUID.randomUUID(), patient = UUID.randomUUID(),
                        sender = UUID.randomUUID(), recipient = UUID.randomUUID();
                try (var insert = connection.prepareStatement("""
                        insert into referral_request (id, organisation_id, request_id, patient_registration_id,
                            sender_user_id, sender_membership_id, sender_display_name_snapshot, recipient_user_id,
                            recipient_membership_id, recipient_display_name_snapshot, reason, priority, purpose,
                            consent_type, consent_evidence_reference, consent_recorded_at, access_expires_at,
                            submitted_immediately, status, created_at, last_action_by_user_id)
                        values (?, ?, ?, ?, ?, ?, 'Synthetic Sender', ?, ?, 'Synthetic Recipient',
                            'Synthetic opinion', 'ROUTINE', 'Synthetic second opinion', 'RECORDED_WRITTEN',
                            'synthetic-consent', current_timestamp, current_timestamp + interval '1 day',
                            true, 'ACTIVE', current_timestamp, ?)
                        """)) {
                    UUID[] values = {id, org, UUID.randomUUID(), patient, sender, UUID.randomUUID(), recipient, UUID.randomUUID(), sender};
                    for (int index = 0; index < values.length; index++) insert.setObject(index + 1, values[index]);
                    insert.executeUpdate();
                }
                try (var grant = connection.prepareStatement("""
                        insert into referral_sharing_grant (id, referral_id, organisation_id, patient_registration_id,
                            recipient_user_id, recipient_membership_id, status, valid_from, valid_until)
                        select ?, id, organisation_id, patient_registration_id, recipient_user_id,
                            recipient_membership_id, 'ACTIVE', created_at, access_expires_at from referral_request
                        """)) {
                    grant.setObject(1, UUID.randomUUID()); grant.executeUpdate();
                }
                try (var item = connection.prepareStatement("""
                        insert into referral_share_item (id, referral_id, organisation_id, resource_type, resource_id, created_at)
                        select ?, id, organisation_id, 'DIAGNOSIS', ?, created_at from referral_request
                        """)) {
                    item.setObject(1, UUID.randomUUID()); item.setObject(2, UUID.randomUUID()); item.executeUpdate();
                }
                statement.execute(migration("V4__typed_referrals_and_care_participation.sql"));
                try (var result = statement.executeQuery("select referral_type, status from referral_request")) {
                    assertTrue(result.next()); assertEquals("SECOND_OPINION", result.getString(1)); assertEquals("ACTIVE", result.getString(2));
                }
                try (var result = statement.executeQuery("select (select count(*) from referral_care_participation), (select count(*) from referral_share_item)")) {
                    assertTrue(result.next()); assertEquals(0, result.getInt(1)); assertEquals(1, result.getInt(2));
                }
                reject(connection, "update referral_request set referral_type = 'SHARED_TREATMENT'");
                reject(connection, """
                        insert into referral_care_participation (id, grant_id, doctor_user_id, doctor_membership_id, started_at)
                        select gen_random_uuid(), id, recipient_user_id, recipient_membership_id, valid_from from referral_sharing_grant
                        """);
            }
            finally {
                // Removes only this test's transactional schema and restores the prior search path.
                connection.rollback(start); connection.releaseSavepoint(start);
            }
            return null;
        });
    }

    private static String migration(String name) {
        try (var stream = new ClassPathResource("db/migration/" + name).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (java.io.IOException unavailable) { throw new IllegalStateException("Missing migration", unavailable); }
    }
    private static void reject(Connection connection, String sql) throws SQLException {
        var savepoint = connection.setSavepoint();
        try (var statement = connection.createStatement()) { assertThrows(SQLException.class, () -> statement.execute(sql)); }
        finally { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
    }
}
