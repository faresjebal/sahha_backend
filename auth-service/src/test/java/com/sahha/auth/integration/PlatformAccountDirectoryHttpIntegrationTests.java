package com.sahha.auth.integration;

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

import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformAccountDirectoryHttpIntegrationTests {

	private static final String PLATFORM_TOKEN = "platform.account.directory";
	private static final String USER_TOKEN = "ordinary.account.directory";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void platformAdministratorResolvesOnlySafeIdentityFieldsByExactEmail()
			throws Exception {
		UserAccount account = verifiedAccount("assignable-" + UUID.randomUUID());
		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(PLATFORM_TOKEN, List.of("PLATFORM_ADMIN")));

		mockMvc.perform(get("/api/v1/auth/platform/accounts")
					.cookie(new Cookie("SAHHA_ACCESS_TOKEN", PLATFORM_TOKEN))
					.param("email", "  " + account.getEmail().toUpperCase() + "  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(account.getId().toString()))
				.andExpect(jsonPath("$.email").value(account.getEmail()))
				.andExpect(jsonPath("$.firstName").value("Synthetic"))
				.andExpect(jsonPath("$.lastName").value("Administrator"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.emailVerified").value(true))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.credentialVersion").doesNotExist());
	}

	@Test
	void nonPlatformUserAndUnknownEmailAreDeniedSafely() throws Exception {
		when(jwtDecoder.decode(USER_TOKEN)).thenReturn(
				jwt(USER_TOKEN, List.of()));
		mockMvc.perform(get("/api/v1/auth/platform/accounts")
					.cookie(new Cookie("SAHHA_ACCESS_TOKEN", USER_TOKEN))
					.param("email", "someone@example.test"))
				.andExpect(status().isForbidden());

		when(jwtDecoder.decode(PLATFORM_TOKEN)).thenReturn(
				jwt(PLATFORM_TOKEN, List.of("PLATFORM_ADMIN")));
		mockMvc.perform(get("/api/v1/auth/platform/accounts")
					.cookie(new Cookie("SAHHA_ACCESS_TOKEN", PLATFORM_TOKEN))
					.param("email", "missing@example.test"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:auth-resource-not-found"));
	}

	private UserAccount verifiedAccount(String localPart) {
		String email = localPart + "@example.test";
		Instant registeredAt = Instant.parse("2026-07-31T10:00:00Z");
		UserAccount account = UserAccount.pendingRegistration(
				email,
				email,
				"$2a$12$synthetic.password.hash.for.directory.testing",
				"Synthetic",
				"Administrator",
				"+21670000000",
				registeredAt);
		account.verifyEmail(registeredAt.plusSeconds(60));
		return userRepository.saveAndFlush(account);
	}

	private static Jwt jwt(String token, List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token)
				.header("alg", "RS256")
				.subject(UUID.randomUUID().toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", roles)
				.claim("token_type", "access")
				.build();
	}
}
