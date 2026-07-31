package com.sahha.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.auth.entity.PlatformRoleCode;
import com.sahha.auth.entity.UserPlatformRole;

public interface UserPlatformRoleRepository extends JpaRepository<UserPlatformRole, UUID> {

	List<UserPlatformRole> findAllByUser_IdAndActiveTrue(UUID userId);

	@Query("""
			select assignment
			from UserPlatformRole assignment
			join fetch assignment.role
			where assignment.user.id = :userId
			  and assignment.active = true
			""")
	List<UserPlatformRole> findAllActiveWithRoleByUserId(
			@Param("userId") UUID userId);

	Optional<UserPlatformRole> findByUser_IdAndRole_Code(
			UUID userId,
			PlatformRoleCode roleCode);

	boolean existsByUser_IdAndRole_CodeAndActiveTrue(
			UUID userId,
			PlatformRoleCode roleCode);
}
