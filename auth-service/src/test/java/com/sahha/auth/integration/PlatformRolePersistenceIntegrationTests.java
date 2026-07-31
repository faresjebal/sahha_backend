package com.sahha.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.PlatformRole;
import com.sahha.auth.entity.PlatformRoleCode;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserPlatformRole;
import com.sahha.auth.repository.PlatformRoleRepository;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.UserPlatformRoleRepository;

@SpringBootTest
class PlatformRolePersistenceIntegrationTests {

	@Autowired
	private PlatformRoleRepository roleRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private UserPlatformRoleRepository assignmentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Transactional
	void migrationSeedsOnlyTheGlobalPlatformAdminRole() {
		PlatformRole role = roleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN)
				.orElseThrow();
		List<String> roleCodes = jdbcTemplate.queryForList(
				"SELECT code FROM platform_role ORDER BY code",
				String.class);

		assertEquals(
				java.util.UUID.fromString("a0000000-0000-4000-8000-000000000001"),
				role.getId());
		assertFalse(role.getDescription().isBlank());
		assertNotNull(role.getCreatedAt());
		assertNotNull(role.getUpdatedAt());
		assertEquals(List.of("PLATFORM_ADMIN"), roleCodes);
		assertFalse(roleCodes.contains("ORGANIZATION_ADMIN"));
		assertFalse(roleCodes.contains("DOCTOR"));
		assertFalse(roleCodes.contains("RECEPTIONIST"));
		assertTrue(role.toString().contains(role.getId().toString()));
		assertTrue(role.toString().contains(PlatformRoleCode.PLATFORM_ADMIN.name()));
		assertFalse(role.toString().contains(role.getDescription()));
	}

	@Test
	@Transactional
	void assignsFindsAndDeactivatesAPlatformRole() {
		UserAccount assigner = userRepository.saveAndFlush(
				pendingAccount("assigner@example.com"));
		UserAccount target = userRepository.saveAndFlush(
				pendingAccount("target@example.com"));
		PlatformRole role = roleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN)
				.orElseThrow();

		UserPlatformRole assignment = assignmentRepository.saveAndFlush(
				UserPlatformRole.assign(target, role, assigner));

		assertTrue(assignment.isActive());
		assertNotNull(assignment.getAssignedAt());
		assertEquals(assigner.getId(), assignment.getAssignedBy().getId());
		assertTrue(assignmentRepository.existsByUser_IdAndRole_CodeAndActiveTrue(
				target.getId(),
				PlatformRoleCode.PLATFORM_ADMIN));
		assertEquals(
				1,
				assignmentRepository.findAllByUser_IdAndActiveTrue(target.getId()).size());
		assertTrue(assignment.toString().contains(assignment.getId().toString()));
		assertTrue(assignment.toString().contains("active=true"));
		assertFalse(assignment.toString().contains("target@example.com"));
		assertFalse(assignment.toString().contains(PlatformRoleCode.PLATFORM_ADMIN.name()));

		Instant deactivatedAt = Instant.now();
		assignment.deactivate(deactivatedAt);
		assignmentRepository.saveAndFlush(assignment);

		assertFalse(assignment.isActive());
		assertEquals(deactivatedAt, assignment.getDeactivatedAt());
		assertFalse(assignmentRepository.existsByUser_IdAndRole_CodeAndActiveTrue(
				target.getId(),
				PlatformRoleCode.PLATFORM_ADMIN));
	}

	@Test
	void generatedRelationshipGettersRemainExcludedFromJson() throws NoSuchMethodException {
		assertJsonIgnored("getUser");
		assertJsonIgnored("getRole");
		assertJsonIgnored("getAssignedBy");
	}

	@Test
	@Transactional
	void databaseRejectsDuplicateUserRoleAssignment() {
		UserAccount target = userRepository.saveAndFlush(
				pendingAccount("duplicate-role@example.com"));
		PlatformRole role = roleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN)
				.orElseThrow();

		assignmentRepository.saveAndFlush(UserPlatformRole.assign(target, role, null));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> assignmentRepository.saveAndFlush(
						UserPlatformRole.assign(target, role, null)));
	}

	@Test
	@Transactional
	void databaseRejectsInactiveAssignmentWithoutDeactivationTime() {
		UserAccount target = userRepository.saveAndFlush(
				pendingAccount("invalid-deactivation@example.com"));
		PlatformRole role = roleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN)
				.orElseThrow();

		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(
						"""
						INSERT INTO user_platform_role (
						    id,
						    user_id,
						    role_id,
						    active
						) VALUES (?, ?, ?, FALSE)
						""",
						java.util.UUID.randomUUID(),
						target.getId(),
						role.getId()));
	}

	private UserAccount pendingAccount(String email) {
		return UserAccount.pendingRegistration(
				email,
				email,
				"synthetic-password-hash",
				"Synthetic",
				"User",
				null);
	}

	private void assertJsonIgnored(String getterName) throws NoSuchMethodException {
		assertNotNull(UserPlatformRole.class
				.getMethod(getterName)
				.getAnnotation(JsonIgnore.class));
	}
}
