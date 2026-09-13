package com.example.backend.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
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
 *
 * Sessions expire on a sliding window: a token is valid for
 * {@link #SESSION_TTL_MILLIS} since its LAST use, not since login, so an
 * active user is never logged out mid-session, but a token that is never
 * used again (browser closed, laptop lost, etc.) stops working on its own
 * instead of living forever until the server restarts.
 */
@Component
public class AuthTokenStore {

    /** Idle timeout: 24h of inactivity invalidates the token. */
    private static final long SESSION_TTL_MILLIS = 24L * 60 * 60 * 1000;

    private static final class Session {
        final Long userId;
        volatile long lastUsedAt;

        Session(Long userId, long lastUsedAt) {
            this.userId = userId;
            this.lastUsedAt = lastUsedAt;
        }
    }

    private final ConcurrentHashMap<String, Session> tokenToSession = new ConcurrentHashMap<>();

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        tokenToSession.put(token, new Session(userId, now()));
        return token;
    }

    /**
     * Resolves a token to a userId, enforcing the idle timeout. A valid,
     * in-use token has its "last used" timestamp refreshed (sliding expiry).
     * An expired token is dropped from the store and treated as invalid.
     */
    public Long resolveUserId(String token) {
        if (token == null) return null;
        Session session = tokenToSession.get(token);
        if (session == null) return null;

        long nowMillis = now();
        if (nowMillis - session.lastUsedAt > SESSION_TTL_MILLIS) {
            tokenToSession.remove(token);
            return null;
        }
        session.lastUsedAt = nowMillis;
        return session.userId;
    }

    public void revoke(String token) {
        if (token != null) tokenToSession.remove(token);
    }

    private static long now() {
        return Instant.now().toEpochMilli();
    }
}
