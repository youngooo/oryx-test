package org.oryxos.core.port;

import java.time.Instant;

@FunctionalInterface
public interface ClockProvider {
    Instant now();

    static ClockProvider systemUtc() {
        return Instant::now;
    }
}
