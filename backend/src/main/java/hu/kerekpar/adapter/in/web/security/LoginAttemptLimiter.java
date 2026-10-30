package hu.kerekpar.adapter.in.web.security;

import hu.kerekpar.domain.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Egyszerű, memóriabeli védelem a jelszótalálgatás ellen: egy felhasználónévre {@code maxFailures} sikertelen kísérlet
 * után {@code lockDuration} ideig nem fogad újabbat. Egy példányon belül működik (több példánynál közös tároló kell),
 * és a felhasználónévre kulcsol, ezért a név szándékos zárolására is használható (ismert kompromisszum).
 */
public class LoginAttemptLimiter {

    private static final int MAX_KEY_LENGTH = 100;
    private static final int PURGE_THRESHOLD = 10_000;

    private record State(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration lockDuration, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public void checkAllowed(String username) {
        State state = states.get(key(username));
        if (state != null && state.lockedUntil() != null && clock.instant().isBefore(state.lockedUntil())) {
            throw new TooManyRequestsException("TOO_MANY_LOGIN_ATTEMPTS",
                    "Túl sok sikertelen bejelentkezési kísérlet, próbáld újra később.");
        }
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (states.size() > PURGE_THRESHOLD) {
            states.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        }
        states.compute(key(username), (k, current) -> {
            if (current == null || isExpired(current, now)) {
                return new State(1, now, maxFailures <= 1 ? now.plus(lockDuration) : null);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockDuration) : current.lockedUntil();
            return new State(failures, current.windowStart(), lockedUntil);
        });
    }

    public void reset(String username) {
        states.remove(key(username));
    }

    private boolean isExpired(State state, Instant now) {
        Instant end = state.lockedUntil() != null ? state.lockedUntil() : state.windowStart().plus(lockDuration);
        return !now.isBefore(end);
    }

    private static String key(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_KEY_LENGTH ? normalized.substring(0, MAX_KEY_LENGTH) : normalized;
    }
}
