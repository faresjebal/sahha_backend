package com.sahha.organisation.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.OrganisationType;
import com.sahha.organisation.repository.OrganisationMembershipRepository;
import com.sahha.organisation.repository.OrganisationMembershipRoleRepository;
import com.sahha.organisation.repository.OrganisationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchedulingDirectoryHttpIntegrationTests {

	private static final String RECEPTIONIST_TOKEN = "directory.reception.token";
	private static final String DOCTOR_TOKEN = "directory.doctor.token";
	private static final String OTHER_DOCTOR_TOKEN = "directory.other-doctor.token";
	private static final String UNRELATED_TOKEN = "directory.unrelated.token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private OrganisationMembershipRoleRepository roleRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void receptionistResolvesOnlyAnActiveDoctorInOwnOrganisation()
			throws Exception {
		Instant now = Instant.now();
		UUID platformActor = UUID.randomUUID();
		Organisation organisation = organisationRepository.saveAndFlush(
				Organisation.createActive(
						"Synthetic scheduling clinic " + UUID.randomUUID(),
						"Synthetic Scheduling Clinic Legal",
						OrganisationType.CLINIC,
						"scheduling@example.test",
						"+216 71 000 000",
						"12 Synthetic Avenue",
						"Tunis",
						"Tunis",
						"1000",
						"TN",
						"Africa/Tunis",
						platformActor,
						now));
		UUID receptionistUserId = UUID.randomUUID();
		OrganisationMembership receptionist = membershipRepository.saveAndFlush(
				OrganisationMembership.activate(
						organisation.getId(),
						receptionistUserId,
						"reception@example.test",
						"Synthetic",
						"Receptionist",
						platformActor,
						now));
		roleRepository.saveAndFlush(OrganisationMembershipRole.assign(
				receptionist,
				OrganisationRole.RECEPTIONIST,
				platformActor,
				now));

		UUID doctorUserId = UUID.randomUUID();
		OrganisationMembership doctor = membershipRepository.saveAndFlush(
				OrganisationMembership.activate(
						organisation.getId(),
						doctorUserId,
						"doctor@example.test",
						"Meriem",
						"Synthetic",
						platformActor,
						now));
		roleRepository.saveAndFlush(OrganisationMembershipRole.assign(
				doctor,
				OrganisationRole.DOCTOR,
				platformActor,
				now));

		when(jwtDecoder.decode(RECEPTIONIST_TOKEN)).thenReturn(jwt(
				RECEPTIONIST_TOKEN, receptionistUserId));
		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/scheduling-doctors/{doctorUserId}",
					organisation.getId(),
					doctorUserId)
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.membershipId")
						.value(doctor.getId().toString()))
				.andExpect(jsonPath("$.organisationId")
						.value(organisation.getId().toString()))
				.andExpect(jsonPath("$.userId")
						.value(doctorUserId.toString()))
				.andExpect(jsonPath("$.displayName")
						.value("Meriem Synthetic"));

		when(jwtDecoder.decode(DOCTOR_TOKEN)).thenReturn(jwt(
				DOCTOR_TOKEN, doctorUserId));
		mockMvc.perform(get(
				"/api/v1/organisations/{organisationId}/scheduling-doctors/{doctorUserId}",
				organisation.getId(),
				doctorUserId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.membershipId")
						.value(doctor.getId().toString()));

		UUID otherDoctorUserId = UUID.randomUUID();
		OrganisationMembership otherDoctor = membershipRepository.saveAndFlush(
				OrganisationMembership.activate(
						organisation.getId(),
						otherDoctorUserId,
						"other-doctor@example.test",
						"Other",
						"Synthetic",
						platformActor,
						now));
		roleRepository.saveAndFlush(OrganisationMembershipRole.assign(
				otherDoctor,
				OrganisationRole.DOCTOR,
				platformActor,
				now));
		when(jwtDecoder.decode(OTHER_DOCTOR_TOKEN)).thenReturn(jwt(
				OTHER_DOCTOR_TOKEN, otherDoctorUserId));
		mockMvc.perform(get(
				"/api/v1/organisations/{organisationId}/scheduling-doctors/{doctorUserId}",
				organisation.getId(),
				doctorUserId)
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());

		when(jwtDecoder.decode(UNRELATED_TOKEN)).thenReturn(jwt(
				UNRELATED_TOKEN, UUID.randomUUID()));
		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/scheduling-doctors/{doctorUserId}",
					organisation.getId(),
					doctorUserId)
					.cookie(access(UNRELATED_TOKEN)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/patient-doctors/{doctorUserId}",
					organisation.getId(),
					doctorUserId)
					.cookie(access(UNRELATED_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.membershipId")
						.value(doctor.getId().toString()))
				.andExpect(jsonPath("$.displayName")
						.value("Meriem Synthetic"));

		when(jwtDecoder.decode(DOCTOR_TOKEN)).thenReturn(jwt(
				DOCTOR_TOKEN, doctorUserId, List.of("DOCTOR"), organisation.getId()));
		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/collaboration-doctors",
					organisation.getId())
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].displayName").exists())
				.andExpect(jsonPath("$.content[1].displayName").exists());

		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/collaboration-doctors/{doctorUserId}",
					organisation.getId(), otherDoctorUserId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(otherDoctorUserId.toString()));

		when(jwtDecoder.decode(OTHER_DOCTOR_TOKEN)).thenReturn(jwt(
				OTHER_DOCTOR_TOKEN, otherDoctorUserId, List.of("DOCTOR"), UUID.randomUUID()));
		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/collaboration-doctors",
					organisation.getId())
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());

		when(jwtDecoder.decode(RECEPTIONIST_TOKEN)).thenReturn(jwt(
				RECEPTIONIST_TOKEN, receptionistUserId, List.of("RECEPTIONIST"), organisation.getId()));
		mockMvc.perform(get(
					"/api/v1/organisations/{organisationId}/collaboration-doctors",
					organisation.getId())
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Jwt jwt(String token, UUID userId) {
		return jwt(token, userId, List.of(), null);
	}

	private static Jwt jwt(String token, UUID userId, List<String> organisationRoles,
			UUID organisationId) {
		Instant now = Instant.now();
		Jwt.Builder builder = Jwt.withTokenValue(token)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_roles", organisationRoles)
				.claim("token_type", "access");
		if (!organisationRoles.isEmpty()) {
			builder.claim("org_id", organisationId.toString());
		}
		return builder.build();
	}
}
