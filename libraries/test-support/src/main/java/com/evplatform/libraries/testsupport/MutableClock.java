package com.evplatform.libraries.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Deterministic clock for tests: starts at a fixed instant and advances only
 * when callers advance it. Technical primitive only.
 */
public final class MutableClock extends Clock {

    private volatile Instant now;

    private MutableClock(Instant start) {
        this.now = start;
    }

    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    @Override
    public ZoneOffset getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
        return this; // deliberately zone-independent
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }
}
