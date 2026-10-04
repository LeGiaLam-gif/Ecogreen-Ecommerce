package com.example.backend.security;

import java.io.InputStream;
import java.util.Properties;

/** Loads the TEST-ONLY signing secret from src/test/resources/test-jwt.properties. */
public final class TestJwt {
    public static final String SECRET = load();

    private TestJwt() {}

    private static String load() {
        try (InputStream in = TestJwt.class.getClassLoader().getResourceAsStream("test-jwt.properties")) {
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("ecogreen.jwt.secret");
        } catch (Exception e) {
            throw new IllegalStateException("test-jwt.properties missing", e);
        }
    }
}
