package com.sahha.scheduling.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class SchedulingPermissions {
    private SchedulingPermissions() { }
    public static final String AVAILABILITY_SELF = "scheduling:availability:manage:self";
    public static final String DIRECTORY_READ = "scheduling:availability:read";
    public static final String APPOINTMENT_READ = "scheduling:appointment:read";
    public static final String BOOK = "scheduling:appointment:book";
    public static final String ADJUST = "scheduling:appointment:adjust";
    public static final String CHECK_IN = "scheduling:appointment:check-in";
    public static final String RESPOND = "scheduling:appointment:respond";
    public static final String TREAT = "scheduling:appointment:treat";
    public static final String CLINICAL_CONTEXT = "scheduling:clinical-context:read";
    public static final String PATIENT_SELF = "scheduling:appointment:manage:patient-self";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(), Map.of(
            "ORGANIZATION_ADMIN", Set.of(DIRECTORY_READ, APPOINTMENT_READ, BOOK, ADJUST, CHECK_IN),
            "RECEPTIONIST", Set.of(DIRECTORY_READ, APPOINTMENT_READ, BOOK, ADJUST, CHECK_IN),
            "DOCTOR", Set.of(AVAILABILITY_SELF, DIRECTORY_READ, APPOINTMENT_READ, ADJUST, RESPOND, TREAT, CLINICAL_CONTEXT)), Set.of(PATIENT_SELF));
    }
}
