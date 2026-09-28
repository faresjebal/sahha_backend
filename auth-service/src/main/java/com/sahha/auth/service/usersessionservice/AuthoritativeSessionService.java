package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.repository.UserPlatformRoleRepository;
import com.sahha.auth.repository.UserSessionRepository;

@Service
public class AuthoritativeSessionService {
    private final UserSessionRepository sessions;
    private final UserPlatformRoleRepository roles;

    public AuthoritativeSessionService(UserSessionRepository sessions, UserPlatformRoleRepository roles) {
        this.sessions = sessions;
        this.roles = roles;
    }

    /** Security decisions must not use the eventually refreshed Redis projection. */
    @Transactional(readOnly = true)
    public boolean isActiveForContext(UUID sessionId, UUID userId, int credentialVersion,
            UUID organisationId, List<String> organisationRoles, List<String> platformRoles, Instant now) {
        return sessions.findByIdWithUser(sessionId).filter(session -> {
            var user = session.getUser();
            return user.getId().equals(userId) && user.canAuthenticate()
                    && user.getEmailVerifiedAt() != null
                    && user.getCredentialVersion() == credentialVersion
                    && session.isActiveAt(now, credentialVersion)
                    && Objects.equals(session.getActiveOrganisationId(), organisationId)
                    && session.getActiveOrganisationRoles().equals(organisationRoles)
                    && roles.findAllActiveWithRoleByUserId(userId).stream()
                            .map(assignment -> assignment.getRole().getCode().name())
                            .distinct().sorted().toList().equals(platformRoles);
        }).isPresent();
    }
}
