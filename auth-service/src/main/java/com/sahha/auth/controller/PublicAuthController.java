package com.sahha.auth.controller;

import java.time.Clock;
import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.auth.dto.request.EmailAddressRequest;
import com.sahha.auth.dto.request.RegisterAccountRequest;
import com.sahha.auth.dto.request.ResetPasswordRequest;
import com.sahha.auth.dto.request.VerificationTokenRequest;
import com.sahha.auth.dto.response.AcceptedResponse;
import com.sahha.auth.security.ClientNetworkAddressResolver;
import com.sahha.auth.service.ratelimitservice.AuthRateLimitScope;
import com.sahha.auth.service.ratelimitservice.AuthRateLimitService;
import com.sahha.auth.service.useraccountservice.PublicAccountWorkflowService;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirement(name = "csrfHeader")
@Tag(
		name = "Public authentication",
		description = """
				Public account lifecycle endpoints. No access token is required. \
				Email and password-recovery requests deliberately use a generic \
				response so callers cannot discover whether an account exists.
				""")
public class PublicAuthController {

	private final PublicAccountWorkflowService workflowService;
	private final AuthRateLimitService rateLimitService;
	private final ClientNetworkAddressResolver clientAddressResolver;
	private final Clock clock;

	public PublicAuthController(
			PublicAccountWorkflowService workflowService,
			AuthRateLimitService rateLimitService,
			ClientNetworkAddressResolver clientAddressResolver,
			Clock clock) {
		this.workflowService = workflowService;
		this.rateLimitService = rateLimitService;
		this.clientAddressResolver = clientAddressResolver;
		this.clock = clock;
	}

	@PostMapping("/registrations")
	@Operation(
			operationId = "registerAccount",
			summary = "Register a patient account",
			description = """
					Creates a pending patient account and requests an email-verification \
					message. The response never contains account identifiers or raw tokens.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "202",
					description = "Registration request accepted",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = AcceptedResponse.class))),
			@ApiResponse(
					responseCode = "400",
					description = "The request body is malformed or invalid",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "409",
					description = "The request conflicts with the current account state",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	public ResponseEntity<AcceptedResponse> register(
			@Valid @RequestBody RegisterAccountRequest request,
			HttpServletRequest servletRequest) {
		Instant requestedAt = clock.instant();
		throttle(
				AuthRateLimitScope.REGISTRATION,
				servletRequest,
				requestedAt);
		workflowService.register(
				request.email(),
				request.password(),
				request.firstName(),
				request.lastName(),
				request.phoneNumber(),
				requestedAt);
		return ResponseEntity.accepted().body(AcceptedResponse.accepted());
	}

	@PostMapping("/email-verifications/confirm")
	@Operation(
			operationId = "confirmEmail",
			summary = "Confirm an email address",
			description = "Consumes a single-use email-verification token.")
	@ApiResponses({
			@ApiResponse(
					responseCode = "204",
					description = "Email address confirmed"),
			@ApiResponse(
					responseCode = "400",
					description = "The token is invalid, expired, used, or malformed",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "409",
					description = "The account state does not allow confirmation",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	public ResponseEntity<Void> confirmEmail(
			@Valid @RequestBody VerificationTokenRequest request,
			HttpServletRequest servletRequest) {
		Instant confirmedAt = clock.instant();
		throttle(
				AuthRateLimitScope.EMAIL_VERIFICATION_CONFIRMATION,
				servletRequest,
				confirmedAt);
		workflowService.confirmEmail(request.token(), confirmedAt);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/email-verifications/resend")
	@Operation(
			operationId = "resendEmailVerification",
			summary = "Request another verification email",
			description = """
					Requests another verification email when the account is eligible and \
					the cooldown has elapsed. Always returns the same accepted response.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "202",
					description = "Request accepted",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = AcceptedResponse.class))),
			@ApiResponse(
					responseCode = "400",
					description = "The email field is malformed or invalid",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	public ResponseEntity<AcceptedResponse> resendEmailVerification(
			@Valid @RequestBody EmailAddressRequest request,
			HttpServletRequest servletRequest) {
		Instant requestedAt = clock.instant();
		throttle(
				AuthRateLimitScope.EMAIL_VERIFICATION_REQUEST,
				servletRequest,
				requestedAt);
		workflowService.requestEmailVerification(
				request.email(),
				requestedAt);
		return ResponseEntity.accepted().body(AcceptedResponse.accepted());
	}

	@PostMapping("/password-resets/request")
	@Operation(
			operationId = "requestPasswordReset",
			summary = "Request a password-reset email",
			description = """
					Requests a reset message for an eligible account. Always returns the \
					same accepted response to prevent account enumeration.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "202",
					description = "Request accepted",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = AcceptedResponse.class))),
			@ApiResponse(
					responseCode = "400",
					description = "The email field is malformed or invalid",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	public ResponseEntity<AcceptedResponse> requestPasswordReset(
			@Valid @RequestBody EmailAddressRequest request,
			HttpServletRequest servletRequest) {
		Instant requestedAt = clock.instant();
		throttle(
				AuthRateLimitScope.PASSWORD_RESET_REQUEST,
				servletRequest,
				requestedAt);
		workflowService.requestPasswordReset(
				request.email(),
				requestedAt);
		return ResponseEntity.accepted().body(AcceptedResponse.accepted());
	}

	@PostMapping("/password-resets/confirm")
	@Operation(
			operationId = "confirmPasswordReset",
			summary = "Set a new password",
			description = """
					Consumes a single-use reset token, replaces the password, and revokes \
					the user's active sessions.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "204",
					description = "Password replaced and active sessions revoked"),
			@ApiResponse(
					responseCode = "400",
					description = "The token or new password is invalid",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "409",
					description = "The account state does not allow password reset",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	public ResponseEntity<Void> resetPassword(
			@Valid @RequestBody ResetPasswordRequest request,
			HttpServletRequest servletRequest) {
		Instant changedAt = clock.instant();
		throttle(
				AuthRateLimitScope.PASSWORD_RESET_CONFIRMATION,
				servletRequest,
				changedAt);
		workflowService.resetPassword(
				request.token(),
				request.newPassword(),
				changedAt);
		return ResponseEntity.noContent().build();
	}

	private void throttle(
			AuthRateLimitScope scope,
			HttpServletRequest request,
			Instant observedAt) {
		rateLimitService.check(
				scope,
				clientAddressResolver.resolve(request),
				observedAt);
	}
}
