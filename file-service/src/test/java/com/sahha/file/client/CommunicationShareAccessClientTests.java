package com.sahha.file.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.URI;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.sahha.file.client.communication.CommunicationShareAccessClient;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.exception.SharingContextUnavailableException;

class CommunicationShareAccessClientTests {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private final UUID patient = UUID.randomUUID(), resource = UUID.randomUUID(),
            owner = UUID.randomUUID(), membership = UUID.randomUUID();
    private MockRestServiceServer server;
    private CommunicationShareAccessClient client;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://communication-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CommunicationShareAccessClient(builder.build(),
                new FileSecurityProperties("SAHHA_ACCESS_TOKEN",
                        URI.create("http://localhost/jwks"), "http://localhost", "sahha-api",
                        "XSRF-TOKEN", "X-XSRF-TOKEN", false), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void forwardsExactOwnerContextAndOnlyTheCurrentCredential() {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "SAHHA_ACCESS_TOKEN=aaa.bbb.ccc"))
                .andExpect(header("X-Request-ID", "selected-read"))
                .andRespond(withSuccess(allowed(NOW.plusSeconds(60)), MediaType.APPLICATION_JSON));
        assertEquals(NOW.plusSeconds(60), read());
        server.verify();
    }

    @Test void deniesExpiredOrNegativeDecisionsAndConcealsUpstreamRoleDenials() {
        for (String body : java.util.List.of("{\"allowed\":false}", allowed(NOW))) {
            server.reset();
            server.expect(requestTo(url())).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(FileResourceNotFoundException.class, this::read);
            server.verify();
        }
        for (HttpStatus status : java.util.List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            server.reset();
            server.expect(requestTo(url())).andRespond(withStatus(status));
            assertThrows(FileResourceNotFoundException.class, this::read);
            server.verify();
        }
    }

    @Test void failsClosedForMalformedResponsesAndUnavailableAuthority() {
        for (String body : java.util.List.of("{}", "{\"allowed\":true}", "not-json")) {
            server.reset();
            server.expect(requestTo(url())).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(SharingContextUnavailableException.class, this::read);
            server.verify();
        }
        server.reset();
        server.expect(requestTo(url())).andRespond(withServerError());
        assertThrows(SharingContextUnavailableException.class, this::read);
        server.verify();
    }

    private Instant read() {
        return client.requireGrant(patient, "CONSULTATION", resource, owner, membership,
                "aaa.bbb.ccc", "selected-read");
    }
    private String url() {
        return "http://communication-service/api/v1/sharing/access-decisions?patientRegistrationId="
                + patient + "&resourceType=CONSULTATION&resourceId=" + resource
                + "&resourceOwnerUserId=" + owner + "&resourceOwnerMembershipId=" + membership;
    }
    private String allowed(Instant until) {
        return "{\"allowed\":true,\"grantId\":\"" + UUID.randomUUID()
                + "\",\"referralId\":\"" + UUID.randomUUID() + "\",\"validUntil\":\"" + until + "\"}";
    }
}
