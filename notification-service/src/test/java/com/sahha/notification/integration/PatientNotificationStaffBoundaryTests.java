package com.sahha.notification.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import com.sahha.notification.security.NotificationAccessTokenValidator;
import com.sahha.notification.security.NotificationRoleConverter;
import com.sahha.notification.security.NotificationWebSocketHandshakeInterceptor;

/** Patient authentication must never imply staff organisation access. */
@SpringBootTest
@AutoConfigureMockMvc
class PatientNotificationStaffBoundaryTests {
    @Autowired MockMvc mvc;
    @Autowired NotificationWebSocketHandshakeInterceptor staffHandshake;
    @MockitoBean JwtDecoder decoder;

    @Test
    void patientTokenCanAuthenticateWithoutInventingAnOrganisation() {
        assertFalse(new NotificationAccessTokenValidator().validate(patient()).hasErrors());
    }

    @Test
    void patientSessionCannotReadStaffInboxOrUnreadCount() throws Exception {
        when(decoder.decode("patient.session.token")).thenReturn(patient());
        for (String path : List.of("/api/v1/notifications", "/api/v1/notifications/unread-count")) {
            mvc.perform(get(path).cookie(new Cookie("SAHHA_ACCESS_TOKEN", "patient.session.token")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void patientSessionCannotOpenStaffWebSocket() {
        Jwt jwt = patient();
        var request = new MockHttpServletRequest();
        request.setUserPrincipal(new JwtAuthenticationToken(jwt, new NotificationRoleConverter().convert(jwt)));
        var response = new MockHttpServletResponse();
        assertFalse(staffHandshake.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>()));
        assertEquals(403, response.getStatus());
    }

    private static Jwt patient() {
        return Jwt.withTokenValue("patient.session.token").header("alg", "RS256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("sid", UUID.randomUUID().toString()).claim("cv", 1).claim("token_type", "access")
                .claim("roles", List.of()).claim("org_roles", List.of()).build();
    }
}
