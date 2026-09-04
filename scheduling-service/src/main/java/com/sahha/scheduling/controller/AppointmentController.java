package com.sahha.scheduling.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.scheduling.config.RequestIdFilter;
import com.sahha.scheduling.dto.request.AppointmentCommandRequest;
import com.sahha.scheduling.dto.request.BookAppointmentRequest;
import com.sahha.scheduling.dto.request.ReasonedAppointmentCommandRequest;
import com.sahha.scheduling.dto.request.RescheduleAppointmentRequest;
import com.sahha.scheduling.dto.response.AppointmentResponse;
import com.sahha.scheduling.dto.response.PatientAppointmentResponse;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.security.SchedulingAccessTokenValidator;
import com.sahha.scheduling.service.appointmentservice.AppointmentBookingResult;
import com.sahha.scheduling.service.appointmentservice.AppointmentBookingService;
import com.sahha.scheduling.service.appointmentservice.AppointmentCommandService;
import com.sahha.scheduling.service.appointmentservice.AppointmentQueryService;

@RestController
@RequestMapping("/api/v1/appointments")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Appointments",
		description = "Organisation-scoped, availability-backed appointment commands.")
public class AppointmentController {

	private final AppointmentBookingService bookingService;
	private final AppointmentQueryService queryService;
	private final AppointmentCommandService commandService;

	public AppointmentController(
			AppointmentBookingService bookingService,
			AppointmentQueryService queryService,
			AppointmentCommandService commandService) {
		this.bookingService = bookingService;
		this.queryService = queryService;
		this.commandService = commandService;
	}

	@GetMapping
	@Operation(
			operationId = "listAppointments",
			summary = "List appointments visible in the active organisation")
	public ResponseEntity<List<AppointmentResponse>> list(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant to,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(queryService.list(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						from,
						to));
	}

	@GetMapping("/mine")
	@Operation(operationId = "listMyPatientAppointments", summary = "List appointment statuses for an owned patient registration")
	public ResponseEntity<List<PatientAppointmentResponse>> listMine(
			@RequestParam UUID registrationId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
					Instant to,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(queryService.listMine(
						registrationId,
						actorUserId(jwt),
						jwt.getTokenValue(),
						from,
						to));
	}

	@GetMapping("/{appointmentId}")
	@Operation(
			operationId = "getAppointment",
			summary = "Read one authorised appointment")
	public ResponseEntity<AppointmentResponse> find(
			@PathVariable UUID appointmentId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(queryService.find(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						appointmentId));
	}

	@PostMapping
	@Operation(
			operationId = "bookAppointment",
			summary = "Request one currently available doctor slot")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> book(
			@Valid @RequestBody BookAppointmentRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		AppointmentBookingResult result = bookingService.book(
				activeOrganisationId(jwt),
				actorUserId(jwt),
				jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest),
				request);
		if (!result.created()) {
			return ResponseEntity.ok()
					.cacheControl(CacheControl.noStore())
					.body(result.appointment());
		}
		return ResponseEntity.created(URI.create(
				"/api/v1/appointments/" + result.appointment().id()))
				.cacheControl(CacheControl.noStore())
				.body(result.appointment());
	}

	@PostMapping("/mine")
	@Operation(operationId = "bookMyPatientAppointment", summary = "Request one slot for the authenticated patient's owned registration")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<PatientAppointmentResponse> bookMine(
			@Valid @RequestBody BookAppointmentRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		AppointmentBookingResult result = bookingService.bookMine(
				actorUserId(jwt),
				jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest),
				request);
		PatientAppointmentResponse response = new PatientAppointmentResponse(
				result.appointment().id(),
				result.appointment().organisationId(),
				result.appointment().doctorUserId(),
				result.appointment().status(),
				result.appointment().statusReason(),
				result.appointment().startsAt(),
				result.appointment().endsAt(),
				result.appointment().timeZone(),
				result.appointment().locationLabel(),
				result.appointment().bookedAt(),
				result.appointment().updatedAt(),
				result.appointment().version());
		if (!result.created()) {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
		}
		return ResponseEntity.created(URI.create(
				"/api/v1/appointments/mine/" + response.id()))
				.cacheControl(CacheControl.noStore())
				.body(response);
	}

	@PostMapping("/{appointmentId}/confirm")
	@Operation(
			operationId = "confirmAppointment",
			summary = "Confirm an owned doctor appointment")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> confirm(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody AppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.confirm(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/reject")
	@Operation(
			operationId = "rejectAppointment",
			summary = "Reject an owned doctor appointment with a reason")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> reject(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody ReasonedAppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.reject(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/reschedule")
	@Operation(
			operationId = "rescheduleAppointment",
			summary = "Move an appointment to another exact doctor slot")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> reschedule(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody RescheduleAppointmentRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.reschedule(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/cancel")
	@Operation(
			operationId = "cancelAppointment",
			summary = "Cancel an appointment with a reason")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> cancel(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody ReasonedAppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.cancel(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/check-in")
	@Operation(
			operationId = "checkInAppointment",
			summary = "Check in a confirmed patient appointment")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> checkIn(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody AppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.checkIn(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/start")
	@Operation(
			operationId = "startAppointment",
			summary = "Start an owned checked-in doctor appointment")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> start(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody AppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.start(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/complete")
	@Operation(
			operationId = "completeAppointment",
			summary = "Complete an owned in-progress doctor appointment")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> complete(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody AppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.complete(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	@PostMapping("/{appointmentId}/no-show")
	@Operation(
			operationId = "markAppointmentNoShow",
			summary = "Mark a confirmed appointment missed after its start time")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentResponse> noShow(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody AppointmentCommandRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return commandResponse(commandService.markNoShow(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest), appointmentId, request));
	}

	private static ResponseEntity<AppointmentResponse> commandResponse(
			AppointmentResponse response) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(response);
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
