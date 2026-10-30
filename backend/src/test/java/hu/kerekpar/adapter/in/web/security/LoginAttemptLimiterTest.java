package hu.kerekpar.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** Kézzel léptethető óra. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-03-14T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), clock);

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAllowed(username);
            limiter.recordFailure(username);
        }
    }

    @Test
    void attemptsBelowTheLimitAreAllowed() {
        fail("peter", 2);

        limiter.checkAllowed("peter");
    }

    @Test
    void reachingTheLimitLocksTheUser() {
        fail("peter", 3);

        assertThatThrownBy(() -> limiter.checkAllowed("peter"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void lockExpiresAfterTheLockDuration() {
        fail("peter", 3);

        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.checkAllowed("peter")).isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofMinutes(2));
        limiter.checkAllowed("peter");
    }

    @Test
    void successResetsTheCounter() {
        fail("peter", 2);
        limiter.reset("peter");

        fail("peter", 2);

        limiter.checkAllowed("peter");
    }

    @Test
    void oldFailuresOutsideTheWindowAreForgotten() {
        fail("peter", 2);
        clock.advance(Duration.ofMinutes(16));

        fail("peter", 2);

        limiter.checkAllowed("peter");
    }

    @Test
    void usersAreCountedIndependently() {
        fail("peter", 3);

        limiter.checkAllowed("eszter");
    }

    @Test
    void keyIgnoresCaseAndSurroundingWhitespace() {
        fail("Peter", 2);
        limiter.recordFailure("  PETER ");

        assertThatThrownBy(() -> limiter.checkAllowed("peter")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void nullAndBlankUsernamesAreHandledAndShareOneBucket() {
        limiter.recordFailure(null);
        limiter.recordFailure("");
        limiter.recordFailure("   ");

        assertThatThrownBy(() -> limiter.checkAllowed(null)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void limitOfOneLocksOnTheFirstFailure() {
        LoginAttemptLimiter strict = new LoginAttemptLimiter(1, Duration.ofMinutes(1), clock);

        strict.recordFailure("peter");

        assertThatThrownBy(() -> strict.checkAllowed("peter")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void veryLongUsernamesDoNotGrowTheKeyUnbounded() {
        String longName = "a".repeat(5000);
        fail(longName, 3);

        assertThatThrownBy(() -> limiter.checkAllowed(longName + "b")).isInstanceOf(TooManyRequestsException.class);
    }
}
