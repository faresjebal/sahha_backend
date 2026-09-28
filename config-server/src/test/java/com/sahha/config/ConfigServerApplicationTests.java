package com.sahha.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigServerApplicationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void servesSharedOperationalConfigurationForDomainClients() throws Exception {
		mvc.perform(get("/clinical-service/platform"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("clinical-service"))
				.andExpect(jsonPath("$.propertySources[*].source['sahha.platform.config-version']")
						.value(hasItem("native-v1")))
				.andExpect(jsonPath("$.propertySources[*].source['management.endpoint.health.show-details']")
						.value(hasItem("never")));
	}
}
