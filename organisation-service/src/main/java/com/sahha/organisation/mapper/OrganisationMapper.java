package com.sahha.organisation.mapper;

import org.springframework.stereotype.Component;

import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.Organisation;

@Component
public class OrganisationMapper {

	public OrganisationResponse toResponse(Organisation organisation) {
		return new OrganisationResponse(
				organisation.getId(),
				organisation.getName(),
				organisation.getLegalName(),
				organisation.getType(),
				organisation.getStatus(),
				organisation.getContactEmail(),
				organisation.getPhoneNumber(),
				organisation.getAddress(),
				organisation.getCity(),
				organisation.getRegion(),
				organisation.getPostalCode(),
				organisation.getCountryCode(),
				organisation.getTimeZone(),
				organisation.getCreatedBy(),
				organisation.getCreatedAt(),
				organisation.getUpdatedAt(),
				organisation.getVersion());
	}
}
