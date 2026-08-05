package com.sahha.organisation.mapper;

import org.springframework.stereotype.Component;

import com.sahha.organisation.dto.response.DepartmentResponse;
import com.sahha.organisation.entity.Department;

@Component
public class DepartmentMapper {

	public DepartmentResponse toResponse(Department department) {
		return new DepartmentResponse(
				department.getId(),
				department.getOrganisationId(),
				department.getName(),
				department.getCode(),
				department.getDescription(),
				department.getStatus(),
				department.getCreatedBy(),
				department.getUpdatedBy(),
				department.getCreatedAt(),
				department.getUpdatedAt(),
				department.getVersion());
	}
}
