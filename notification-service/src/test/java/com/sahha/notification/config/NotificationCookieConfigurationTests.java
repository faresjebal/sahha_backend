package com.sahha.notification.config;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import com.sahha.notification.security.NotificationAccessTokenCookieResolver;
import static org.junit.jupiter.api.Assertions.*;

class NotificationCookieConfigurationTests {
    private NotificationSecurityProperties bind(Map<String, String> settings) throws IOException {
        // Test shipped configuration rather than a copied test-only placeholder.
        var environment = new MockEnvironment();
        settings.forEach(environment::setProperty);
        environment.getPropertySources().addLast(new ResourcePropertySource(
                new FileSystemResource("src/main/resources/application.properties")));
        return Binder.get(environment).bind("sahha.notification.security",
                NotificationSecurityProperties.class).get();
    }

    @Test
    void defaultsRetainTheExistingCookieContract() throws Exception {
        var properties = bind(Map.of());
        assertEquals("SAHHA_ACCESS_TOKEN", properties.accessTokenCookieName());
        assertEquals("XSRF-TOKEN", properties.csrfTokenName());
        assertFalse(properties.secureCookies());
    }

    @Test
    void canonicalSettingsMatchAuthAndOtherResourceServices() throws Exception {
        var properties = bind(Map.of("AUTH_ACCESS_COOKIE_NAME", "SAHHA_DEMO_ACCESS",
                "AUTH_CSRF_COOKIE_NAME", "SAHHA_DEMO_XSRF", "AUTH_COOKIE_SECURE", "true"));
        assertEquals("SAHHA_DEMO_ACCESS", properties.accessTokenCookieName());
        assertEquals("SAHHA_DEMO_XSRF", properties.csrfTokenName());
        assertTrue(properties.secureCookies());
        var resolver = new NotificationAccessTokenCookieResolver(properties);
        var request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        request.setCookies(new Cookie("SAHHA_DEMO_ACCESS", "synthetic.access.token"));
        assertEquals("synthetic.access.token", resolver.resolve(request));
        request.setCookies(new Cookie("SAHHA_ACCESS_TOKEN", "synthetic.access.token"));
        assertNull(resolver.resolve(request), "An unconfigured cookie must not authenticate");

        var repository = new NotificationSecurityConfiguration().notificationCsrfTokenRepository(properties);
        var response = new MockHttpServletResponse();
        repository.saveToken(new DefaultCsrfToken("X-XSRF-TOKEN", "SAHHA_DEMO_XSRF", "synthetic-csrf"), request, response);
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("SAHHA_DEMO_XSRF=synthetic-csrf;"));
        assertTrue(cookie.contains("Secure"));
        // Spring's mock header serializer omits SameSite for a plain Servlet
        // Cookie. Inspect the actual Servlet 6 attribute supplied to Tomcat.
        Cookie storedCookie = response.getCookie("SAHHA_DEMO_XSRF");
        assertNotNull(storedCookie);
        assertEquals("Lax", storedCookie.getAttribute("SameSite"));
        assertEquals("/", storedCookie.getPath());
        assertFalse(storedCookie.isHttpOnly(), "The SPA must read its CSRF cookie");
    }

    @Test
    void legacyNotificationSettingsRemainCompatibleWhenCanonicalSettingsAreAbsent() throws Exception {
        var properties = bind(Map.of("AUTH_ACCESS_TOKEN_COOKIE_NAME", "LEGACY_ACCESS", "AUTH_SECURE_COOKIES", "true"));
        assertEquals("LEGACY_ACCESS", properties.accessTokenCookieName());
        assertTrue(properties.secureCookies());
    }

    @Test
    void canonicalSettingsTakePrecedenceIncludingExplicitFalse() throws Exception {
        var properties = bind(Map.of("AUTH_ACCESS_COOKIE_NAME", "CANONICAL_ACCESS", "AUTH_COOKIE_SECURE", "false",
                "AUTH_ACCESS_TOKEN_COOKIE_NAME", "LEGACY_ACCESS", "AUTH_SECURE_COOKIES", "true"));
        assertEquals("CANONICAL_ACCESS", properties.accessTokenCookieName());
        assertFalse(properties.secureCookies());
    }

    @Test
    void invalidCanonicalCookieCannotSilentlyFallBackToLegacy() {
        assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                () -> bind(Map.of("AUTH_ACCESS_COOKIE_NAME", "", "AUTH_ACCESS_TOKEN_COOKIE_NAME", "LEGACY_ACCESS")));
    }
}
