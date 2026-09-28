package com.sahha.notification.patient;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;
import com.sahha.notification.security.NotificationPermissions;
import com.sahha.notification.exception.NotificationNotFoundException;
import com.sahha.notification.exception.NotificationAccessDeniedException;

@Component
public class PatientNotificationHandshake extends DefaultHandshakeHandler implements HandshakeInterceptor {
    public static final String ENDPOINT = "/api/v1/notifications/patient/ws";
    public static final String CONTEXT_ATTRIBUTE = PatientNotificationHandshake.class.getName() + ".context";
    private final PatientNotificationAccess access;
    public PatientNotificationHandshake(PatientNotificationAccess access) { this.access = access; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request.getPrincipal() instanceof JwtAuthenticationToken authentication)
                || authentication.getAuthorities().stream().noneMatch(
                    value -> NotificationPermissions.STREAM_SELF.equals(value.getAuthority()))) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }
        try {
            var ids = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().get("registrationId");
            if (ids == null || ids.size() != 1) return reject(response, HttpStatus.BAD_REQUEST);
            var context = access.requireOwnRegistration(UUID.fromString(ids.getFirst()), authentication.getToken());
            attributes.put(CONTEXT_ATTRIBUTE, context);
            return true;
        } catch (NotificationNotFoundException missing) {
            return reject(response, HttpStatus.NOT_FOUND);
        } catch (NotificationAccessDeniedException denied) {
            return reject(response, HttpStatus.FORBIDDEN);
        } catch (PatientNotificationContextUnavailableException unavailable) {
            return reject(response, HttpStatus.SERVICE_UNAVAILABLE);
        } catch (IllegalArgumentException invalid) {
            return reject(response, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attributes) {
        var authentication = (JwtAuthenticationToken) request.getPrincipal();
        var context = (PatientNotificationContext) attributes.get(CONTEXT_ATTRIBUTE);
        return new JwtAuthenticationToken(authentication.getToken(), authentication.getAuthorities(), context.principalName());
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) { }

    private static boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        response.getHeaders().setCacheControl(CacheControl.noStore());
        return false;
    }
}
