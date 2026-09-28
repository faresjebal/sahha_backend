package com.sahha.patient.documentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(locations = "classpath:api-policy.properties")
class ApiContractHttpIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @Test
    void generatedPublicContractIncludesCorrelationProblemsAndCookiePlusCsrf() throws Exception {
        assertTrue(context.containsBean("httpProtocolProblemDetailsHandler"));
        assertEquals("never", context.getEnvironment().getProperty("server.error.include-message"));
        assertEquals("false", context.getEnvironment().getProperty("spring.mvc.log-request-details"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.SahhaProblemDetail.properties.requestId").exists())
                .andExpect(jsonPath("$.components.schemas.SahhaProblemDetail.properties.errors").exists())
                .andExpect(jsonPath("$.paths['/api/v1/patients'].get.parameters[?(@.name == 'X-Request-ID')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/patients'].post.security", hasSize(1)))
                .andExpect(jsonPath("$.paths['/api/v1/patients'].post.security[0].cookieAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/patients'].post.security[0].csrfHeader").exists())
                .andExpect(jsonPath("$.paths['/api/v1/patients'].post.responses['500'].headers['X-Request-ID']").exists())
                .andExpect(content().string(not(containsString("/api/v1/internal/"))));
    }
}
