package com.sahha.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApiGatewayApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.security.oauth2.jwt.ReactiveJwtDecoder decoder;

	@Test
	void productionDecoderEnforcesAuthoritativeSessions() {
		org.junit.jupiter.api.Assertions.assertInstanceOf(com.sahha.session.SessionCheckingReactiveJwtDecoder.class, decoder);
	}

	@Test
	void contextLoads() {
	}

}
