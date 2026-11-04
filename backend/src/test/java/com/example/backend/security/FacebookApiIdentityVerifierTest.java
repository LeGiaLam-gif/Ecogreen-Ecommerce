package com.example.backend.security;

import com.example.backend.exception.FacebookLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B01: the Graph API calls, against a local HTTP server standing in for graph.facebook.com. */
class FacebookApiIdentityVerifierTest {

    private HttpServer server;
    private String host;
    private volatile int debugStatus = 200;
    private volatile String debugBody;
    private volatile int meStatus = 200;
    private volatile String meBody;
    private final AtomicReference<String> debugQuery = new AtomicReference<>();
    private final AtomicReference<String> meQuery = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        debugBody = "{\"data\":{\"app_id\":\"APP1\",\"is_valid\":true,\"user_id\":\"fb-1\"}}";
        meBody = "{\"id\":\"fb-1\",\"name\":\"Fb User\",\"email\":\"fb@example.com\"}";
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/debug_token", ex -> {
            debugQuery.set(ex.getRequestURI().getRawQuery());
            reply(ex, debugStatus, debugBody);
        });
        server.createContext("/me", ex -> {
            meQuery.set(ex.getRequestURI().getRawQuery());
            reply(ex, meStatus, meBody);
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private FacebookApiIdentityVerifier verifier(String appId, String secret) {
        return new FacebookApiIdentityVerifier(appId, secret, host, "", HttpClient.newHttpClient());
    }

    @Test
    void validToken_returnsTheIdentity_andSendsAppTokenAndProof() {
        FacebookIdentity identity = verifier("APP1", "s3cret").verify("user-token");

        assertEquals("fb-1", identity.id());
        assertEquals("Fb User", identity.name());
        assertEquals("fb@example.com", identity.email());
        assertTrue(debugQuery.get().contains("input_token=user-token"));
        assertTrue(debugQuery.get().contains("access_token=APP1%7Cs3cret")); // app access token "APP1|s3cret"
        assertTrue(meQuery.get().contains("appsecret_proof="));
        assertTrue(meQuery.get().contains("fields=id%2Cname%2Cemail") || meQuery.get().contains("fields=id,name,email"));
    }

    @Test
    void emailIsOptional() {
        meBody = "{\"id\":\"fb-1\",\"name\":\"Fb User\"}";
        assertNull(verifier("APP1", "s3cret").verify("user-token").email());
    }

    @Test
    void tokenIssuedForAnotherApp_isRejected() {
        debugBody = "{\"data\":{\"app_id\":\"OTHER\",\"is_valid\":true,\"user_id\":\"fb-1\"}}";
        assertThrows(UnauthorizedException.class, () -> verifier("APP1", "s3cret").verify("user-token"));
    }

    @Test
    void invalidOrExpiredToken_isRejected() {
        debugBody = "{\"data\":{\"app_id\":\"APP1\",\"is_valid\":false}}";
        assertThrows(UnauthorizedException.class, () -> verifier("APP1", "s3cret").verify("user-token"));

        debugStatus = 400;
        debugBody = "{\"error\":{\"code\":190}}";
        assertThrows(UnauthorizedException.class, () -> verifier("APP1", "s3cret").verify("user-token"));
    }

    @Test
    void meIdDifferentFromTokenUser_isRejected() {
        meBody = "{\"id\":\"someone-else\",\"name\":\"X\"}";
        assertThrows(UnauthorizedException.class, () -> verifier("APP1", "s3cret").verify("user-token"));
    }

    @Test
    void facebookDown_orGarbage_failsClosedAsUnavailable() {
        debugStatus = 503;
        assertThrows(FacebookLoginUnavailableException.class, () -> verifier("APP1", "s3cret").verify("user-token"));

        debugStatus = 200;
        debugBody = "<html>not json</html>";
        assertThrows(FacebookLoginUnavailableException.class, () -> verifier("APP1", "s3cret").verify("user-token"));
    }

    @Test
    void notConfigured_failsClosed_withoutAnyRequest() {
        FacebookApiIdentityVerifier v = verifier("", "");
        assertEquals(false, v.isConfigured());
        assertThrows(FacebookLoginUnavailableException.class, () -> v.verify("user-token"));
        assertNull(debugQuery.get());
    }
}
