package com.example.backend.security;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory session/token store.
 *
 * This is intentionally simple (no JWT library, no external session store) to
 * avoid over-engineering the first version, while still giving the backend a
 * real, server-verified notion of "who is calling this endpoint" so that
 * authorization is never left to what the frontend claims about itself.
 *
 * A production system would replace this with signed JWTs or a persisted
 * session table, but the contract (opaque token -> userId, verified on every
 * protected request) stays the same.
 */
@Component
public class AuthTokenStore {

    private final ConcurrentHashMap<String, Long> tokenToUserId = new ConcurrentHashMap<>();

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        tokenToUserId.put(token, userId);
        return token;
    }

    public Long resolveUserId(String token) {
        if (token == null) return null;
        return tokenToUserId.get(token);
    }

    public void revoke(String token) {
        if (token != null) tokenToUserId.remove(token);
    }
}
