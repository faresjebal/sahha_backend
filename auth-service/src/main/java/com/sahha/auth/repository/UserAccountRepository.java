package com.sahha.auth.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.auth.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

	boolean existsByNormalizedEmail(String normalizedEmail);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select account
			from UserAccount account
			where account.normalizedEmail = :normalizedEmail
			""")
	Optional<UserAccount> findByNormalizedEmailForUpdate(
			@Param("normalizedEmail") String normalizedEmail);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from UserAccount account where account.id = :userId")
	Optional<UserAccount> findByIdForUpdate(@Param("userId") UUID userId);
}
