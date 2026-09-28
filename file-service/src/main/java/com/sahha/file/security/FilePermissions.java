package com.sahha.file.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class FilePermissions {
    private FilePermissions() { }
    public static final String READ_OWN = "file:read:own";
    public static final String UPLOAD_OWN = "file:upload:own";
    public static final String READ_SHARED = "file:read:shared";
    public static final String READ_SHARED_CARE = "file:read:shared-care";
    public static final String SYNTHETIC_SCAN = "file:scan:synthetic";
    public static final String UPLOAD_MESSAGE = "file:upload:message";
    public static final String READ_MESSAGE = "file:read:message";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(), Map.of(
            "DOCTOR", Set.of(READ_OWN, UPLOAD_OWN, READ_SHARED, READ_SHARED_CARE, SYNTHETIC_SCAN, UPLOAD_MESSAGE, READ_MESSAGE)), Set.of());
    }
}
