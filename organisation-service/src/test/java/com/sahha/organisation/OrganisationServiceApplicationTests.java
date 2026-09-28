package com.sahha.organisation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrganisationServiceApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void kafkaProducerIsAutoConfiguredWithoutConnectingToABroker() {
		org.junit.jupiter.api.Assertions.assertNotNull(kafkaTemplate.getProducerFactory());
	}

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
