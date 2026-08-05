package com.sahha.organisation.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.organisation.entity.StaffInvitation;

public interface StaffInvitationRepository
		extends JpaRepository<StaffInvitation, UUID> {

	Page<StaffInvitation> findAllByOrganisationId(
			UUID organisationId,
			Pageable pageable);

	Page<StaffInvitation> findAllByNormalizedEmail(
			String normalizedEmail,
			Pageable pageable);

	Optional<StaffInvitation> findByIdAndOrganisationId(
			UUID invitationId,
			UUID organisationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select invitation from StaffInvitation invitation where invitation.id = :invitationId")
	Optional<StaffInvitation> findByIdForUpdate(
			@Param("invitationId") UUID invitationId);
}
