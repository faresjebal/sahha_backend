package com.sahha.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthServiceApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void kafkaProducerIsAutoConfiguredWithoutConnectingToABroker() {
		org.junit.jupiter.api.Assertions.assertNotNull(kafkaTemplate.getProducerFactory());
	}

	@Test
	void contextLoads() {
	}

}
