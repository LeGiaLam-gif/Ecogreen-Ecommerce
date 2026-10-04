package com.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory failed-login throttle keyed by username + client IP: after {@code maxFailures} failures inside the
 * window, further attempts get 429 RATE_LIMITED until the window ends. Per instance only (no Redis by design);
 * a successful login clears the counter. Counts unknown usernames too, so it does not reveal which accounts exist.
 */
@Component
public class LoginThrottle {

    public static class TooManyAttemptsException extends RuntimeException {
        private final long retryAfterSeconds;
        public TooManyAttemptsException(long retryAfterSeconds) {
            super("Bạn đã thử đăng nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút.");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    private static final class Entry { int failures; long windowStartMs; }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long windowMs;
    private final Clock clock;

    public LoginThrottle(@Value("${ecogreen.auth.max-failed-logins:5}") int maxFailures,
                         @Value("${ecogreen.auth.lockout-window-seconds:900}") long windowSeconds) {
        this(maxFailures, windowSeconds, Clock.systemUTC());
    }

    public LoginThrottle(int maxFailures, long windowSeconds, Clock clock) {
        this.maxFailures = maxFailures;
        this.windowMs = windowSeconds * 1000;
        this.clock = clock;
    }

    private static String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) + "|" + ip;
    }

    /** @throws TooManyAttemptsException when this username+IP is currently locked out */
    public void checkAllowed(String username, String ip) {
        Entry e = entries.get(key(username, ip));
        long now = clock.millis();
        if (e != null && now - e.windowStartMs < windowMs && e.failures >= maxFailures) {
            throw new TooManyAttemptsException(Math.max(1, (e.windowStartMs + windowMs - now + 999) / 1000));
        }
    }

    public void recordFailure(String username, String ip) {
        long now = clock.millis();
        if (entries.size() > 10_000) entries.values().removeIf(x -> now - x.windowStartMs >= windowMs); // bound memory
        entries.compute(key(username, ip), (k, e) -> {
            if (e == null || now - e.windowStartMs >= windowMs) { e = new Entry(); e.windowStartMs = now; }
            e.failures++;
            return e;
        });
    }

    public void recordSuccess(String username, String ip) {
        entries.remove(key(username, ip));
    }
}
