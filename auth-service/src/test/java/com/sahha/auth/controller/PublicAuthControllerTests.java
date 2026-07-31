package com.sahha.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.sahha.auth.config.AuthHttpSecurityConfiguration;
import com.sahha.auth.config.RequestIdFilter;
import com.sahha.auth.exception.AuthProblemDetailsHandler;
import com.sahha.auth.exception.InvalidVerificationTokenException;
import com.sahha.auth.service.useraccountservice.PublicAccountWorkflowService;
import com.sahha.auth.security.ClientNetworkAddressResolver;
import com.sahha.auth.service.ratelimitservice.AuthRateLimitService;

@WebMvcTest(PublicAuthController.class)
@Import({
		AuthHttpSecurityConfiguration.class,
		AuthProblemDetailsHandler.class,
		RequestIdFilter.class
})
class PublicAuthControllerTests {

	private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PublicAccountWorkflowService workflowService;

	@MockitoBean
	private AuthRateLimitService rateLimitService;

	@MockitoBean
	private ClientNetworkAddressResolver clientAddressResolver;

	@MockitoBean
	private Clock clock;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void useDeterministicClock() {
		org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
		org.mockito.Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
	}

	@Test
	void registrationReturnsAcceptedWithoutAccountOrSecretData() throws Exception {
		String body = """
				{
				  "email": "synthetic.user@example.com",
				  "password": "synthetic passphrase",
				  "firstName": "Synthetic",
				  "lastName": "User",
				  "phoneNumber": "+21600000000"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/registrations")
						.with(csrf())
						.header(RequestIdFilter.HEADER_NAME, "request-12345")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isAccepted())
				.andExpect(header().string(
						RequestIdFilter.HEADER_NAME,
						"request-12345"))
				.andExpect(jsonPath("$.status").value("accepted"))
				.andExpect(content().string(not(containsString(
						"synthetic.user@example.com"))))
				.andExpect(content().string(not(containsString(
						"synthetic passphrase"))))
				.andExpect(content().string(not(containsString("token"))));

		verify(workflowService).register(
				eq("synthetic.user@example.com"),
				eq("synthetic passphrase"),
				eq("Synthetic"),
				eq("User"),
				eq("+21600000000"),
				eq(NOW));
	}

	@Test
	void invalidRegistrationUsesSafeProblemDetailsAndFieldErrors()
			throws Exception {
		mockMvc.perform(post("/api/v1/auth/registrations")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "not-an-email",
								  "password": "short",
								  "firstName": "",
								  "lastName": "User"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(
						MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:validation"))
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andExpect(jsonPath("$.errors.email").isNotEmpty())
				.andExpect(jsonPath("$.errors.password").isNotEmpty())
				.andExpect(jsonPath("$.errors.firstName").isNotEmpty())
				.andExpect(content().string(not(containsString("short"))));

		verifyNoInteractions(workflowService);
	}

	@Test
	void resendAndPasswordResetRequestsUseTheSameAcceptedContract()
			throws Exception {
		String body = """
				{"email":"synthetic.user@example.com"}
				""";

		mockMvc.perform(post("/api/v1/auth/email-verifications/resend")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("accepted"))
				.andExpect(content().string(not(containsString(
						"synthetic.user@example.com"))));

		mockMvc.perform(post("/api/v1/auth/password-resets/request")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("accepted"))
				.andExpect(content().string(not(containsString(
						"synthetic.user@example.com"))));

		verify(workflowService).requestEmailVerification(
				"synthetic.user@example.com",
				NOW);
		verify(workflowService).requestPasswordReset(
				"synthetic.user@example.com",
				NOW);
	}

	@Test
	void invalidVerificationTokenUsesOneGenericProblemWithoutEchoingToken()
			throws Exception {
		String rawToken = "secret-presented-token";
		doThrow(new InvalidVerificationTokenException())
				.when(workflowService)
				.confirmEmail(rawToken, NOW);

		mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"secret-presented-token"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(
						MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:invalid-auth-token"))
				.andExpect(jsonPath("$.detail").value(
						"The authentication token is invalid or expired."))
				.andExpect(content().string(not(containsString(rawToken))));
	}

	@Test
	void confirmationsReturnNoContent() throws Exception {
		mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"synthetic-verification-token"}
								"""))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "token":"synthetic-password-reset-token",
								  "newPassword":"replacement passphrase"
								}
								"""))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		verify(workflowService).confirmEmail(
				"synthetic-verification-token",
				NOW);
		verify(workflowService).resetPassword(
				"synthetic-password-reset-token",
				"replacement passphrase",
				NOW);
	}
}
