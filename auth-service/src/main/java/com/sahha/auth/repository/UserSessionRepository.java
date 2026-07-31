package com.sahha.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

	List<UserSession> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

	List<UserSession> findAllByUser_IdAndStatusOrderByCreatedAtDesc(
			UUID userId,
			SessionStatus status);

	Optional<UserSession> findByIdAndUser_Id(UUID sessionId, UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from UserSession session where session.id = :sessionId")
	Optional<UserSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

	@Query("""
			select session
			from UserSession session
			join fetch session.user
			where session.id = :sessionId
			""")
	Optional<UserSession> findByIdWithUser(
			@Param("sessionId") UUID sessionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select session
			from UserSession session
			where session.user.id = :userId
			  and session.status = :status
			order by session.createdAt desc
			""")
	List<UserSession> findAllByUserIdAndStatusForUpdate(
			@Param("userId") UUID userId,
			@Param("status") SessionStatus status);
}
