package com.sahha.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	Optional<RefreshToken> findBySession_IdAndUsedAtIsNullAndRevokedAtIsNull(
			UUID sessionId);

	List<RefreshToken> findAllBySession_IdOrderByCreatedAtAsc(UUID sessionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(
			@Param("tokenHash") String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select token
			from RefreshToken token
			where token.session.id = :sessionId
			order by token.createdAt asc
			""")
	List<RefreshToken> findAllBySessionIdForUpdate(
			@Param("sessionId") UUID sessionId);

	@Modifying
	@Query(
			value = """
					DELETE FROM refresh_token token
					USING (
					    SELECT session.id
					    FROM user_session session
					    WHERE session.absolute_expires_at < :purgeBefore
					      AND EXISTS (
					          SELECT 1
					          FROM refresh_token candidate_token
					          WHERE candidate_token.session_id = session.id
					      )
					    ORDER BY session.absolute_expires_at, session.id
					    FOR UPDATE SKIP LOCKED
					    LIMIT :familyBatchSize
					) expired_session
					WHERE token.session_id = expired_session.id
					""",
			nativeQuery = true)
	int deleteExpiredSessionTokenFamilies(
			@Param("purgeBefore") java.time.Instant purgeBefore,
			@Param("familyBatchSize") int familyBatchSize);
}
