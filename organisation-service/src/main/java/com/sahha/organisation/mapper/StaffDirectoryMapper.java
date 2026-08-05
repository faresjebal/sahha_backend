package com.sahha.organisation.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.organisation.dto.response.DoctorProfileResponse;
import com.sahha.organisation.dto.response.StaffDepartmentAssignmentResponse;
import com.sahha.organisation.dto.response.StaffMemberResponse;
import com.sahha.organisation.entity.Department;
import com.sahha.organisation.entity.DoctorProfile;
import com.sahha.organisation.entity.OrganisationMembership;
import com.sahha.organisation.entity.OrganisationMembershipRole;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.entity.StaffDepartmentAssignment;
import com.sahha.organisation.exception.DepartmentNotFoundException;

@Component
public class StaffDirectoryMapper {

	public StaffMemberResponse toResponse(
			OrganisationMembership membership,
			Collection<OrganisationMembershipRole> assignments,
			Collection<StaffDepartmentAssignment> departmentAssignments,
			Map<UUID, Department> departments,
			DoctorProfile doctorProfile) {
		EnumSet<OrganisationRole> roles = EnumSet.noneOf(OrganisationRole.class);
		assignments.stream()
				.map(OrganisationMembershipRole::getRole)
				.filter(role -> role == OrganisationRole.DOCTOR
						|| role == OrganisationRole.RECEPTIONIST)
				.forEach(roles::add);
		List<StaffDepartmentAssignmentResponse> departmentResponses =
				departmentAssignments.stream()
						.sorted(Comparator
								.comparing(
										StaffDepartmentAssignment::isPrimaryAssignment)
								.reversed()
								.thenComparing(
										StaffDepartmentAssignment::getCreatedAt,
										Comparator.reverseOrder()))
						.map(assignment -> assignmentResponse(
								assignment,
								departments.get(assignment.getDepartmentId())))
						.toList();
		return new StaffMemberResponse(
				membership.getId(),
				membership.getOrganisationId(),
				membership.getUserId(),
				membership.getEmailSnapshot(),
				membership.getDisplayNameSnapshot(),
				membership.getStatus(),
				Set.copyOf(roles),
				departmentResponses,
				doctorProfile == null ? null : doctorProfileResponse(doctorProfile),
				membership.getJoinedAt(),
				membership.getUpdatedAt(),
				membership.getVersion());
	}

	public StaffDepartmentAssignmentResponse assignmentResponse(
			StaffDepartmentAssignment assignment,
			Department department) {
		if (department == null) {
			throw new DepartmentNotFoundException();
		}
		return new StaffDepartmentAssignmentResponse(
				assignment.getId(),
				department.getId(),
				department.getName(),
				department.getCode(),
				assignment.getPositionTitle(),
				assignment.isPrimaryAssignment(),
				assignment.getStartDate(),
				assignment.getPlannedEndDate(),
				assignment.getStatus(),
				assignment.getEndedAt(),
				assignment.getCreatedAt(),
				assignment.getUpdatedAt(),
				assignment.getVersion());
	}

	public DoctorProfileResponse doctorProfileResponse(DoctorProfile profile) {
		return new DoctorProfileResponse(
				profile.getId(),
				profile.getOrganisationId(),
				profile.getMembershipId(),
				profile.getSpecialty(),
				profile.getProfessionalTitle(),
				profile.getLicenceNumber(),
				profile.getRegistrationAuthority(),
				profile.getBiography(),
				profile.getCreatedAt(),
				profile.getUpdatedAt(),
				profile.getVersion());
	}
}
