package com.sahha.clinical.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.sahha.clinical.client.organisation.OrganisationDoctorClient;
import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.config.OrganisationClientProperties;
import com.sahha.clinical.exception.*;

class OrganisationDoctorClientTests {
    private final UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), member = UUID.randomUUID();
    private MockRestServiceServer server;
    private OrganisationDoctorClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://organisation-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrganisationDoctorClient(builder.build(), new ClinicalSecurityProperties("SAHHA_ACCESS_TOKEN",
                URI.create("http://localhost/jwks"), "http://localhost", "sahha-api", "XSRF-TOKEN", "X-XSRF-TOKEN", false));
    }
    @Test void resolvesExactCurrentDoctorAndForwardsOnlyTheAccessCredential() {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "SAHHA_ACCESS_TOKEN=source.doctor.token"))
                .andExpect(header("X-Request-ID", "referral-source-test"))
                .andRespond(withSuccess(body(org, actor, member, "0"), MediaType.APPLICATION_JSON));
        assertEquals(member, read()); server.verify();
    }
    @Test void deniesUpstreamRoleOrMembershipDenials() {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            server.reset(); server.expect(requestTo(url())).andRespond(withStatus(status));
            assertThrows(ClinicalAccessDeniedException.class, this::read); server.verify();
        }
    }
    @Test void failsClosedOnWrongIdentityIncompleteResponsesAndOutages() {
        for (String value : List.of("{}", "not-json", body(UUID.randomUUID(), actor, member, "0"),
                body(org, UUID.randomUUID(), member, "0"), body(org, actor, member, "null"),
                body(org, actor, member, "-1"))) {
            server.reset(); server.expect(requestTo(url())).andRespond(withSuccess(value, MediaType.APPLICATION_JSON));
            assertThrows(OrganisationContextUnavailableException.class, this::read); server.verify();
        }
        server.reset(); server.expect(requestTo(url())).andRespond(withServerError());
        assertThrows(OrganisationContextUnavailableException.class, this::read); server.verify();
    }
    @Test void rejectsInvalidClientConfiguration() {
        for (String value : List.of("relative", "file:///private", "http://user:password@organisation-service")) {
            assertThrows(IllegalArgumentException.class, () -> new OrganisationClientProperties(URI.create(value)));
        }
        assertThrows(IllegalArgumentException.class, () -> new OrganisationClientProperties(null));
    }
    private UUID read() { return client.requireCurrentMembership(org, actor, "source.doctor.token", "referral-source-test"); }
    private String url() { return "http://organisation-service/api/v1/organisations/" + org + "/collaboration-doctors/" + actor; }
    private String body(UUID organisation, UUID user, UUID membership, String version) {
        return "{\"organisationId\":\"" + organisation + "\",\"userId\":\"" + user + "\",\"membershipId\":\""
                + membership + "\",\"membershipVersion\":" + version + "}";
    }
}
