package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties") // TEST-ONLY JWT secret (JWT_SECRET is mandatory at startup)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
