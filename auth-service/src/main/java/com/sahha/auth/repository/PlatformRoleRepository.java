package com.sahha.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.auth.entity.PlatformRole;
import com.sahha.auth.entity.PlatformRoleCode;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, UUID> {

	Optional<PlatformRole> findByCode(PlatformRoleCode code);

	boolean existsByCode(PlatformRoleCode code);
}
