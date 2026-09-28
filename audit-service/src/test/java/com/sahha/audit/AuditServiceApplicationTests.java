package com.sahha.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.sahha.audit.fixture.AuditTestDatabaseConfiguration;

@SpringBootTest
@Import(AuditTestDatabaseConfiguration.class)
class AuditServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
