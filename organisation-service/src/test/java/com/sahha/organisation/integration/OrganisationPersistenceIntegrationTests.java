package com.sahha.organisation.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.response.OrganisationMembershipResponse;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationMembershipService;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@SpringBootTest
@Transactional
class OrganisationPersistenceIntegrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OrganisationService organisationService;

	@Autowired
	private OrganisationAuditEventRepository auditRepository;

	@Autowired
	private OrganisationOutboxEventRepository outboxRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private OrganisationMembershipRoleRepository membershipRoleRepository;

	@Autowired
	private OrganisationMembershipService membershipService;

	@Test
	void migrationCreatesOwnedTablesAtCurrentVersion() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("sahha_organisation", connection.getCatalog());
		}
		assertEquals(
				"organisation_test",
				jdbcTemplate.queryForObject(
						"SELECT current_schema()",
						String.class));
		assertEquals(
				"organisation",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation')::text",
						String.class));
		assertEquals(
				"organisation_audit_event",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation_audit_event')::text",
						String.class));
		assertEquals(
				"organisation_outbox_event",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation_outbox_event')::text",
						String.class));
		assertEquals(
				"organisation_membership",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation_membership')::text",
						String.class));
		assertEquals(
				"organisation_membership_role",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.organisation_membership_role')::text",
						String.class));
		assertEquals(
				"department",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.department')::text",
						String.class));
		assertEquals(
				"staff_invitation",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.staff_invitation')::text",
						String.class));
		assertEquals(
				"staff_department_assignment",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.staff_department_assignment')::text",
						String.class));
		assertEquals(
				"doctor_profile",
				jdbcTemplate.queryForObject(
						"SELECT to_regclass('organisation_test.doctor_profile')::text",
						String.class));
		assertNotNull(flyway.info().current());
		assertEquals("6", flyway.info().current().getVersion().getVersion());
	}

	@Test
	void creationPersistsOrganisationAuditAndSecretFreeOutboxAtomically() {
		UUID actor = UUID.randomUUID();
		OrganisationResponse created = organisationService.create(
				request("Persistence " + UUID.randomUUID()),
				actor,
				"organisation-persistence-request");

		assertEquals(actor, created.createdBy());
		assertEquals("TN", created.countryCode());
		assertEquals("Africa/Tunis", created.timeZone());
		assertEquals(1, auditRepository.countByOrganisationId(created.id()));
		OrganisationAuditEvent audit = auditRepository.findAll()
				.stream()
				.filter(event -> event.getOrganisationId().equals(created.id()))
				.findFirst()
				.orElseThrow();
		var outbox = outboxRepository.findByAuditEventId(audit.getId())
				.orElseThrow();
		assertEquals(created.id(), outbox.getOrganisationId());
		assertEquals(
				"ORGANISATION_CREATED",
				outbox.getPayload().get("eventType"));
		assertFalse(outbox.getPayload().containsKey("contactEmail"));
		assertFalse(outbox.getPayload().containsKey("address"));
		assertFalse(outbox.getPayload().containsKey("phoneNumber"));
	}

	@Test
	void databaseRejectsDuplicateNormalizedOrganisationName() {
		String name = "Duplicate " + UUID.randomUUID();
		organisationService.create(
				request(name),
				UUID.randomUUID(),
				"organisation-duplicate-first");

		assertThrows(
				DataAccessException.class,
				() -> organisationService.create(
						request("  " + name.toUpperCase() + "  "),
						UUID.randomUUID(),
						"organisation-duplicate-second"));
	}

	@Test
	void auditRowsAreAppendOnly() {
		OrganisationResponse created = organisationService.create(
				request("Audit " + UUID.randomUUID()),
				UUID.randomUUID(),
				"organisation-audit-request");
		OrganisationAuditEvent audit = auditRepository.findAll()
				.stream()
				.filter(event -> event.getOrganisationId().equals(created.id()))
				.findFirst()
				.orElseThrow();

		assertThrows(
				DataAccessException.class,
				() -> jdbcTemplate.update(
						"DELETE FROM organisation_audit_event WHERE id = ?",
						audit.getId()));
	}

	@Test
	void administratorAssignmentPersistsMembershipRoleAuditAndSecretFreeOutbox() {
		UUID actor = UUID.randomUUID();
		OrganisationResponse organisation = organisationService.create(
				request("Membership " + UUID.randomUUID()),
				actor,
				"membership-organisation-create");
		long outboxBefore = outboxRepository.count();
		UUID targetUser = UUID.randomUUID();

		OrganisationMembershipResponse membership =
				membershipService.assignAdministrator(
						organisation.id(),
						new AuthAccountResource(
								targetUser,
								"admin@example.test",
								"Synthetic",
								"Administrator",
								"ACTIVE",
								true),
						actor,
						"membership-assignment");

		assertEquals(targetUser, membership.userId());
		assertEquals("admin@example.test", membership.email());
		assertEquals(1, membershipRepository.count());
		assertEquals(1, membershipRoleRepository.count());
		assertEquals(outboxBefore + 1, outboxRepository.count());
		var audit = auditRepository.findAll().stream()
				.filter(event -> "MEMBERSHIP".equals(event.getResourceType()))
				.filter(event -> membership.id().equals(event.getResourceId()))
				.findFirst()
				.orElseThrow();
		assertEquals(targetUser, audit.getTargetUserId());
		var outbox = outboxRepository.findByAuditEventId(audit.getId())
				.orElseThrow();
		assertEquals(
				"ORGANISATION_ADMIN_ASSIGNED",
				outbox.getPayload().get("eventType"));
		assertEquals("ORGANIZATION_ADMIN", outbox.getPayload().get("role"));
		assertFalse(outbox.getPayload().containsKey("email"));
		assertFalse(outbox.getPayload().containsKey("displayName"));
	}

	@Test
	void duplicateOrganisationMembershipIsRejected() {
		OrganisationResponse organisation = organisationService.create(
				request("Membership duplicate " + UUID.randomUUID()),
				UUID.randomUUID(),
				"membership-duplicate-create");
		UUID targetUser = UUID.randomUUID();
		AuthAccountResource account = new AuthAccountResource(
				targetUser,
				"duplicate@example.test",
				"Duplicate",
				"Administrator",
				"ACTIVE",
				true);
		membershipService.assignAdministrator(
				organisation.id(),
				account,
				UUID.randomUUID(),
				"membership-duplicate-first");

		assertThrows(
				com.sahha.organisation.exception
						.OrganisationMembershipConflictException.class,
				() -> membershipService.assignAdministrator(
						organisation.id(),
						account,
						UUID.randomUUID(),
						"membership-duplicate-second"));
	}

	private static CreateOrganisationRequest request(String name) {
		return new CreateOrganisationRequest(
				name,
				name + " Legal",
				OrganisationType.CLINIC,
				"contact@example.com",
				"+216 71 000 000",
				"12 Synthetic Avenue",
				"Tunis",
				"Tunis",
				"1000",
				"tn",
				"Africa/Tunis");
	}
}
