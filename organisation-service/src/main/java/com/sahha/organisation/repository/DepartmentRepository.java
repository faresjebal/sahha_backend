package com.sahha.organisation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.organisation.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

	Optional<Department> findByIdAndOrganisationId(
			UUID departmentId,
			UUID organisationId);

	Page<Department> findAllByOrganisationId(
			UUID organisationId,
			Pageable pageable);
}
