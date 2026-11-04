package com.example.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetThrottleTest {

    /** Clock the test can move forward. */
    private static final class MovableClock extends Clock {
        final AtomicLong millis = new AtomicLong(Instant.parse("2026-10-30T10:00:00Z").toEpochMilli());
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
    }

    @Test
    void sameIp_isLimited_evenWithDifferentAddresses() {
        PasswordResetThrottle throttle = new PasswordResetThrottle(3, 900, new MovableClock());
        for (int i = 0; i < 3; i++) {
            int n = i;
            assertDoesNotThrow(() -> throttle.checkAndRecord("1.1.1.1", "user" + n + "@example.com"));
        }
        PasswordResetThrottle.TooManyRequestsException ex = assertThrows(PasswordResetThrottle.TooManyRequestsException.class,
                () -> throttle.checkAndRecord("1.1.1.1", "another@example.com"));
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 900);
        assertDoesNotThrow(() -> throttle.checkAndRecord("2.2.2.2", "fresh@example.com")); // other client unaffected
    }

    @Test
    void sameAddress_isLimited_acrossDifferentIps_caseInsensitively() {
        PasswordResetThrottle throttle = new PasswordResetThrottle(2, 900, new MovableClock());
        throttle.checkAndRecord("1.1.1.1", "Victim@Example.com");
        throttle.checkAndRecord("2.2.2.2", "victim@example.com");
        assertThrows(PasswordResetThrottle.TooManyRequestsException.class, () -> throttle.checkAndRecord("3.3.3.3", "VICTIM@example.com"));
    }

    @Test
    void windowExpiry_resetsTheCounter() {
        MovableClock clock = new MovableClock();
        PasswordResetThrottle throttle = new PasswordResetThrottle(1, 900, clock);
        throttle.checkAndRecord("1.1.1.1", "a@example.com");
        assertThrows(PasswordResetThrottle.TooManyRequestsException.class, () -> throttle.checkAndRecord("1.1.1.1", "a@example.com"));

        clock.millis.addAndGet(901_000);
        assertDoesNotThrow(() -> throttle.checkAndRecord("1.1.1.1", "a@example.com"));
    }
}
