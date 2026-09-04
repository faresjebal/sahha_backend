package com.sahha.patient.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.patient.config.RequestIdFilter;
import com.sahha.patient.dto.request.LinkPatientAccountRequest;
import com.sahha.patient.dto.response.MyPatientRegistrationResponse;
import com.sahha.patient.dto.response.PatientAccountLinkResponse;
import com.sahha.patient.dto.response.PatientSchedulingContextResponse;
import com.sahha.patient.exception.PatientAccessDeniedException;
import com.sahha.patient.service.patientaccountservice.PatientAccountService;

@RestController
@RequestMapping("/api/v1/patients/me")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "My patient account", description = "Explicit patient-account ownership and minimum self-service registration data.")
public class MyPatientAccountController {

	private final PatientAccountService accountService;

	public MyPatientAccountController(PatientAccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping("/account-link")
	@Operation(operationId = "linkMyPatientAccount", summary = "Link the verified account to an existing patient registration")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<PatientAccountLinkResponse> link(
			@Valid @RequestBody LinkPatientAccountRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		PatientAccountLinkResponse response = accountService.link(
				actorUserId(jwt),
				jwt.getTokenValue(),
				request,
				RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(URI.create("/api/v1/patients/me/account-link"))
				.cacheControl(CacheControl.noStore())
				.body(response);
	}

	@GetMapping("/registrations")
	@Operation(operationId = "listMyPatientRegistrations", summary = "List only registrations owned by the linked account")
	public ResponseEntity<List<MyPatientRegistrationResponse>> registrations(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(accountService.registrations(actorUserId(jwt)));
	}

	@GetMapping("/registrations/{registrationId}/scheduling-context")
	@Operation(operationId = "getMyPatientSchedulingContext", summary = "Resolve a minimum owned registration context for scheduling")
	public ResponseEntity<PatientSchedulingContextResponse> schedulingContext(
			@PathVariable UUID registrationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(accountService.schedulingContext(actorUserId(jwt), registrationId));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new PatientAccessDeniedException();
		}
	}
}
