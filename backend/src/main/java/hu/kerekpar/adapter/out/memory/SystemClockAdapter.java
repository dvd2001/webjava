package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.ClockPort;
import java.time.Instant;

public class SystemClockAdapter implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
