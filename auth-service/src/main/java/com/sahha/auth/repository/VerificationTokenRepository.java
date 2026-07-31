package com.sahha.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.auth.entity.VerificationToken;
import com.sahha.auth.entity.VerificationTokenPurpose;

public interface VerificationTokenRepository
		extends JpaRepository<VerificationToken, UUID> {

	Optional<VerificationToken> findByTokenHash(String tokenHash);

	List<VerificationToken> findAllByUser_IdAndPurposeOrderByCreatedAtDesc(
			UUID userId,
			VerificationTokenPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select token
			from VerificationToken token
			where token.tokenHash = :tokenHash
			""")
	Optional<VerificationToken> findByTokenHashForUpdate(
			@Param("tokenHash") String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select token
			from VerificationToken token
			where token.user.id = :userId
			  and token.purpose = :purpose
			  and token.usedAt is null
			  and token.revokedAt is null
			""")
	List<VerificationToken> findActiveByUserAndPurposeForUpdate(
			@Param("userId") UUID userId,
			@Param("purpose") VerificationTokenPurpose purpose);
}
