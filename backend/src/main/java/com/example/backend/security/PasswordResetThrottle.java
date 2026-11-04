package com.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory limit on "forgot password" requests: at most {@code maxRequests} per window for each client IP and for each
 * e-mail address. EVERY request counts, whether or not the address belongs to an account, so the limit cannot be used to
 * find out which addresses exist. Per instance only (no Redis by design), like {@link LoginThrottle}.
 */
@Component
public class PasswordResetThrottle {

    public static class TooManyRequestsException extends RuntimeException {
        private final long retryAfterSeconds;
        public TooManyRequestsException(long retryAfterSeconds) {
            super("Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau ít phút.");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    private static final class Entry {
        final long windowStartMs;
        int count;
        Entry(long windowStartMs) { this.windowStartMs = windowStartMs; }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;

    public PasswordResetThrottle(@Value("${ecogreen.auth.reset-max-requests:5}") int maxRequests,
                                 @Value("${ecogreen.auth.reset-window-seconds:900}") long windowSeconds) {
        this(maxRequests, windowSeconds, Clock.systemUTC());
    }

    public PasswordResetThrottle(int maxRequests, long windowSeconds, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMs = windowSeconds * 1000;
        this.clock = clock;
    }

    /** Counts one request for this IP and this address. @throws TooManyRequestsException when either is over the limit */
    public void checkAndRecord(String ip, String email) {
        long now = clock.millis();
        if (entries.size() > 10_000) entries.values().removeIf(e -> now - e.windowStartMs >= windowMs); // bound memory
        long retryIp = hit("ip:" + ip, now);
        long retryMail = hit("email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)), now);
        long retry = Math.max(retryIp, retryMail);
        if (retry > 0) throw new TooManyRequestsException(retry);
    }

    /** @return 0 when allowed, otherwise the seconds until the window ends */
    private long hit(String key, long now) {
        Entry e = entries.compute(key, (k, cur) -> {
            Entry next = (cur == null || now - cur.windowStartMs >= windowMs) ? new Entry(now) : cur;
            next.count++;
            return next;
        });
        return e.count > maxRequests ? Math.max(1, (e.windowStartMs + windowMs - now + 999) / 1000) : 0;
    }
}
