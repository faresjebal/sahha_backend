package com.sahha.scheduling.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.scheduling.dto.request.UpsertDoctorAvailabilityRequest;
import com.sahha.scheduling.dto.response.AvailableSlotsResponse;
import com.sahha.scheduling.dto.response.DoctorAvailabilityResponse;
import com.sahha.scheduling.dto.response.DoctorAvailabilitySummaryResponse;
import com.sahha.scheduling.dto.response.PatientDoctorAvailabilityResponse;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.security.SchedulingAccessTokenValidator;
import com.sahha.scheduling.service.availabilityservice.DoctorAvailabilityService;

@RestController
@RequestMapping("/api/v1/availability")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Doctor availability",
		description = "Organisation-scoped weekly rules, time off, and deterministic booking slots.")
public class DoctorAvailabilityController {

	private final DoctorAvailabilityService availabilityService;

	public DoctorAvailabilityController(
			DoctorAvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}

	@GetMapping("/me")
	@Operation(
			operationId = "getMyDoctorAvailability",
			summary = "Read the current doctor's availability")
	public ResponseEntity<DoctorAvailabilityResponse> findMine(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(availabilityService.findMine(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	@PutMapping("/me")
	@Operation(
			operationId = "upsertMyDoctorAvailability",
			summary = "Create or replace the current doctor's availability")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<DoctorAvailabilityResponse> upsertMine(
			@Valid @RequestBody UpsertDoctorAvailabilityRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(availabilityService.upsertMine(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						request));
	}

	@GetMapping("/doctors")
	@Operation(
			operationId = "listDoctorsWithAvailability",
			summary = "List doctors with published availability in the active organisation")
	public ResponseEntity<List<DoctorAvailabilitySummaryResponse>> listDoctors(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(availabilityService.listDoctors(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	@GetMapping("/mine/registrations/{registrationId}/doctors")
	@Operation(operationId = "listMyAvailableDoctors", summary = "List doctors with availability for an owned patient registration")
	public ResponseEntity<List<PatientDoctorAvailabilityResponse>> patientDoctors(
			@PathVariable UUID registrationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(availabilityService.listPatientDoctors(
						registrationId,
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	@GetMapping("/mine/registrations/{registrationId}/doctors/{doctorUserId}/slots")
	@Operation(operationId = "listMyAvailableDoctorSlots", summary = "List available slots within an owned patient registration")
	public ResponseEntity<AvailableSlotsResponse> patientSlots(
			@PathVariable UUID registrationId,
			@PathVariable UUID doctorUserId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(availabilityService.patientSlots(
						registrationId,
						doctorUserId,
						from,
						to,
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	@GetMapping("/doctors/{doctorUserId}/slots")
	@Operation(
			operationId = "listAvailableDoctorSlots",
			summary = "Calculate available slots for one doctor")
	public ResponseEntity<AvailableSlotsResponse> slots(
			@PathVariable UUID doctorUserId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(availabilityService.slots(
						activeOrganisationId(jwt),
						doctorUserId,
						from,
						to,
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new SchedulingAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					SchedulingAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new SchedulingAccessDeniedException();
		}
	}
}
