package com.sahha.communication.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class CommunicationPermissions {
    private CommunicationPermissions() { }
    public static final String CONVERSATION_READ = "communication:conversation:read:participant";
    public static final String CONVERSATION_WRITE = "communication:conversation:write:participant";
    public static final String MESSAGE_STREAM = "communication:message:stream:participant";
    public static final String REFERRAL_READ = "communication:referral:read:participant";
    public static final String REFERRAL_MANAGE = "communication:referral:manage:participant";
    public static final String SHARING_DECIDE = "communication:sharing:decide";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(), Map.of(
            "DOCTOR", Set.of(CONVERSATION_READ, CONVERSATION_WRITE, MESSAGE_STREAM, REFERRAL_READ, REFERRAL_MANAGE, SHARING_DECIDE)), Set.of());
    }
}
