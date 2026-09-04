package com.sahha.file.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.sahha.file.controller.SyntheticFileScanController;

@SpringBootTest(properties =
		"sahha.file.storage.synthetic-clean-enabled=false")
class SyntheticScanDisabledIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void productionDefaultDoesNotExposeTheSyntheticScanController() {
		assertTrue(applicationContext
				.getBeansOfType(SyntheticFileScanController.class)
				.isEmpty());
	}
}
