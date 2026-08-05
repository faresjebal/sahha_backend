package com.sahha.organisation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sahha.organisation.client.auth.AuthAccountDirectoryClient;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.config.OrganisationSecurityProperties;
import com.sahha.organisation.exception.EligibleAccountNotFoundException;

class AuthAccountDirectoryClientTests {

	private MockRestServiceServer server;
	private AuthAccountDirectoryClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("http://auth-service");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AuthAccountDirectoryClient(
				builder.build(),
				new OrganisationSecurityProperties(
						"SAHHA_ACCESS_TOKEN",
						URI.create("http://localhost:8081/.well-known/jwks.json"),
						"http://localhost:8081",
						"sahha-api",
						"XSRF-TOKEN",
						"X-XSRF-TOKEN",
						false));
	}

	@Test
	void forwardsOnlyTheShortLivedAccessCredentialAndResolvesIdentity() {
		server.expect(requestTo(
					"http://auth-service/api/v1/auth/platform/accounts?email=admin@example.test"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(
						HttpHeaders.COOKIE,
						"SAHHA_ACCESS_TOKEN=aaa.bbb.ccc"))
				.andRespond(withSuccess("""
						{
						  "id":"0ce89700-962e-41a1-b298-8ff7b17f0b5c",
						  "email":"admin@example.test",
						  "firstName":"Leila",
						  "lastName":"Mansour",
						  "status":"ACTIVE",
						  "emailVerified":true
						}
						""", MediaType.APPLICATION_JSON));

		AuthAccountResource account = client.findByEmail(
				"admin@example.test",
				"aaa.bbb.ccc");

		assertEquals("Leila", account.firstName());
		assertEquals(true, account.eligibleForMembership());
		server.verify();
	}

	@Test
	void convertsUnknownIdentityToSafeMembershipNotFound() {
		server.expect(requestTo(
					"http://auth-service/api/v1/auth/platform/accounts?email=missing@example.test"))
				.andRespond(withResourceNotFound());

		assertThrows(
				EligibleAccountNotFoundException.class,
				() -> client.findByEmail(
						"missing@example.test",
						"aaa.bbb.ccc"));
		server.verify();
	}

	@Test
	void resolvesOnlyTheAuthenticatedCallersOwnAccount() {
		server.expect(requestTo("http://auth-service/api/v1/auth/account"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(
						HttpHeaders.COOKIE,
						"SAHHA_ACCESS_TOKEN=caller.access.token"))
				.andRespond(withSuccess("""
						{
						  "id":"0ce89700-962e-41a1-b298-8ff7b17f0b5c",
						  "email":"doctor@example.test",
						  "firstName":"Synthetic",
						  "lastName":"Doctor",
						  "status":"ACTIVE",
						  "emailVerified":true
						}
						""", MediaType.APPLICATION_JSON));

		AuthAccountResource account = client.currentAccount(
				"caller.access.token");

		assertEquals("doctor@example.test", account.email());
		assertEquals(true, account.eligibleForMembership());
		server.verify();
	}
}
