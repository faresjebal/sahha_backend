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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.patient.config.RequestIdFilter;
import com.sahha.patient.dto.request.CreatePatientRegistrationRequest;
import com.sahha.patient.dto.request.DuplicateCheckRequest;
import com.sahha.patient.dto.request.UpdatePatientRegistrationRequest;
import com.sahha.patient.dto.response.DuplicateCheckResponse;
import com.sahha.patient.dto.response.PatientAdministrativePageResponse;
import com.sahha.patient.dto.response.PatientAdministrativeResponse;
import com.sahha.patient.dto.response.PatientAuditHistoryResponse;
import com.sahha.patient.exception.PatientAccessDeniedException;
import com.sahha.patient.security.PatientAccessTokenValidator;
import com.sahha.patient.service.patientregistrationservice.PatientRegistrationService;

@RestController
@RequestMapping("/api/v1/patients")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Administrative patient registry",
		description = "Tenant-scoped receptionist and organisation-administrator operations with no clinical fields.")
public class PatientAdministrativeController {

	private final PatientRegistrationService registrationService;

	public PatientAdministrativeController(
			PatientRegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/duplicate-check")
	@Operation(
			operationId = "checkPatientDuplicates",
			summary = "Find safely masked duplicate candidates")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<DuplicateCheckResponse> checkDuplicates(
			@Valid @RequestBody DuplicateCheckRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(registrationService.checkDuplicates(
						activeOrganisationId(jwt),
						request,
						actorUserId(jwt),
						jwt.getTokenValue(),
						RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping
	@Operation(
			operationId = "createPatientRegistration",
			summary = "Register a patient in the active organisation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<PatientAdministrativeResponse> create(
			@Valid @RequestBody CreatePatientRegistrationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		PatientAdministrativeResponse created = registrationService.create(
				activeOrganisationId(jwt),
				request,
				actorUserId(jwt),
				jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(
					URI.create("/api/v1/patients/" + created.registrationId()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	@GetMapping
	@Operation(
			operationId = "listPatientRegistrations",
			summary = "Search the active organisation patient directory")
	public ResponseEntity<PatientAdministrativePageResponse> list(
			@RequestParam(defaultValue = "") String query,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(registrationService.list(
						activeOrganisationId(jwt),
						query,
						page,
						size,
						actorUserId(jwt),
						jwt.getTokenValue(),
						RequestIdFilter.requestId(httpRequest)));
	}

	@GetMapping("/{registrationId}")
	@Operation(
			operationId = "getPatientRegistration",
			summary = "Read one administrative patient registration")
	public ResponseEntity<PatientAdministrativeResponse> find(
			@PathVariable UUID registrationId,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(registrationService.find(
						activeOrganisationId(jwt),
						registrationId,
						actorUserId(jwt),
						jwt.getTokenValue(),
						RequestIdFilter.requestId(httpRequest)));
	}

	@PutMapping("/{registrationId}")
	@Operation(
			operationId = "updatePatientRegistration",
			summary = "Update administrative patient information")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<PatientAdministrativeResponse> update(
			@PathVariable UUID registrationId,
			@Valid @RequestBody UpdatePatientRegistrationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(registrationService.update(
						activeOrganisationId(jwt),
						registrationId,
						request,
						actorUserId(jwt),
						jwt.getTokenValue(),
						RequestIdFilter.requestId(httpRequest)));
	}

	@GetMapping("/{registrationId}/history")
	@Operation(
			operationId = "listPatientAdministrativeHistory",
			summary = "List append-only administrative patient activity")
	public ResponseEntity<List<PatientAuditHistoryResponse>> history(
			@PathVariable UUID registrationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(registrationService.history(
						activeOrganisationId(jwt),
						registrationId,
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new PatientAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					PatientAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new PatientAccessDeniedException();
		}
	}
}
