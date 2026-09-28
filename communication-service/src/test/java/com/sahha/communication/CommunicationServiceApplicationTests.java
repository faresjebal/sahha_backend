package com.sahha.communication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CommunicationServiceApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.security.oauth2.jwt.JwtDecoder decoder;

	@Test
	void productionDecoderEnforcesAuthoritativeSessions() {
		org.junit.jupiter.api.Assertions.assertInstanceOf(com.sahha.session.SessionCheckingJwtDecoder.class, decoder);
	}

	@Test
	void contextLoads() {
	}
}
