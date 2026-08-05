package com.sahha.organisation.controller;

import java.net.URI;
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

import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.ChangeDepartmentStatusRequest;
import com.sahha.organisation.dto.request.CreateDepartmentRequest;
import com.sahha.organisation.dto.request.UpdateDepartmentRequest;
import com.sahha.organisation.dto.response.DepartmentPageResponse;
import com.sahha.organisation.dto.response.DepartmentResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.security.OrganisationAccessTokenValidator;
import com.sahha.organisation.service.departmentservice.DepartmentService;

@RestController
@RequestMapping("/api/v1/departments")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Organisation departments",
		description = "Active-organisation department controls for organisation administrators.")
public class DepartmentController {

	private final DepartmentService departmentService;

	public DepartmentController(DepartmentService departmentService) {
		this.departmentService = departmentService;
	}

	@PostMapping
	@Operation(
			operationId = "createDepartment",
			summary = "Create a department in the active organisation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<DepartmentResponse> create(
			@Valid @RequestBody CreateDepartmentRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		DepartmentResponse created = departmentService.create(
				activeOrganisationId(jwt),
				request,
				actorUserId(jwt),
				RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(
					URI.create("/api/v1/departments/" + created.id()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	@GetMapping
	@Operation(
			operationId = "listDepartments",
			summary = "List departments in the active organisation")
	public ResponseEntity<DepartmentPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(departmentService.list(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						page,
						size));
	}

	@GetMapping("/{departmentId}")
	@Operation(
			operationId = "getDepartment",
			summary = "Read a department in the active organisation")
	public ResponseEntity<DepartmentResponse> find(
			@PathVariable UUID departmentId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(departmentService.find(
						activeOrganisationId(jwt),
						departmentId,
						actorUserId(jwt)));
	}

	@PutMapping("/{departmentId}")
	@Operation(
			operationId = "updateDepartment",
			summary = "Update a department in the active organisation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<DepartmentResponse> update(
			@PathVariable UUID departmentId,
			@Valid @RequestBody UpdateDepartmentRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(departmentService.update(
						activeOrganisationId(jwt),
						departmentId,
						request,
						actorUserId(jwt),
						RequestIdFilter.requestId(httpRequest)));
	}

	@PutMapping("/{departmentId}/status")
	@Operation(
			operationId = "changeDepartmentStatus",
			summary = "Activate or deactivate a department")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<DepartmentResponse> changeStatus(
			@PathVariable UUID departmentId,
			@Valid @RequestBody ChangeDepartmentStatusRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(departmentService.changeStatus(
						activeOrganisationId(jwt),
						departmentId,
						request,
						actorUserId(jwt),
						RequestIdFilter.requestId(httpRequest)));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new OrganisationAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					OrganisationAccessTokenValidator
							.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new OrganisationAccessDeniedException();
		}
	}
}
