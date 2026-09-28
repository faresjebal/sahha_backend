package com.sahha.clinical.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class ClinicalPermissions {
    private ClinicalPermissions() { }
    public static final String RECORD_READ = "clinical:record:read:own";
    public static final String DRAFT_WRITE = "clinical:draft:write:own";
    public static final String FINALIZE = "clinical:record:finalize:own";
    public static final String CORRECT = "clinical:record:correct:own";
    public static final String SUMMARY_READ = "clinical:summary:read:care";
    public static final String SHARED_READ = "clinical:selected-resource:read:shared";
    public static final String SHARED_CARE_READ = "clinical:history:read:shared-care";
    public static final String ATTACHMENT_CONTEXT = "clinical:attachment-context:read:own";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(), Map.of(
            "DOCTOR", Set.of(RECORD_READ, DRAFT_WRITE, FINALIZE, CORRECT, SUMMARY_READ, SHARED_READ, SHARED_CARE_READ, ATTACHMENT_CONTEXT)), Set.of());
    }
}
