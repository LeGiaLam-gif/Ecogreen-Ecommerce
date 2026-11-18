package com.example.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties") // TEST-ONLY JWT secret (JWT_SECRET is mandatory at startup)
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
