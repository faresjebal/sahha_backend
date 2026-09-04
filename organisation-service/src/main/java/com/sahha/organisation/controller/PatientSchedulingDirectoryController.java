package com.sahha.organisation.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.dto.response.SchedulingDoctorResponse;
import com.sahha.organisation.service.schedulingdirectoryservice.SchedulingDirectoryService;

@RestController
@RequestMapping("/api/v1/organisations/{organisationId}/patient-doctors")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Patient doctor directory", description = "Minimum active-doctor projection used by patient-owned appointment requests.")
public class PatientSchedulingDirectoryController {

	private final SchedulingDirectoryService schedulingDirectoryService;

	public PatientSchedulingDirectoryController(
			SchedulingDirectoryService schedulingDirectoryService) {
		this.schedulingDirectoryService = schedulingDirectoryService;
	}

	@GetMapping("/{doctorUserId}")
	@Operation(operationId = "getPatientVisibleSchedulingDoctor", summary = "Resolve an active doctor without exposing staff administration data")
	public ResponseEntity<SchedulingDoctorResponse> find(
			@PathVariable UUID organisationId,
			@PathVariable UUID doctorUserId) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(schedulingDirectoryService.findPatientVisibleActiveDoctor(
						organisationId, doctorUserId));
	}
}
