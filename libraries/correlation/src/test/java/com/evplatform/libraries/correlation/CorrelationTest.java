package com.evplatform.libraries.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationTest {

    @AfterEach
    void resetContext() {
        CorrelationContext.clear();
    }

    @Test
    void generatedIdsAreUniqueAndValidUuids() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String id = CorrelationIds.newId();
            assertTrue(seen.add(id), "duplicate id generated");
            assertTrue(CorrelationIds.isValid(id));
        }
    }

    @Test
    void validationRejectsGarbage() {
        assertFalse(CorrelationIds.isValid(null));
        assertFalse(CorrelationIds.isValid(""));
        assertFalse(CorrelationIds.isValid("not-a-uuid"));
        assertFalse(CorrelationIds.isValid("12345"));
        assertTrue(CorrelationIds.isValid("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void contextGeneratesCorrelationLazilyAndIsolatesThreads() throws Exception {
        String first = CorrelationContext.correlationId();
        assertEquals(first, CorrelationContext.correlationId(), "same thread must keep its correlation id");
        assertNull(CorrelationContext.causationId());

        String[] otherThread = new String[1];
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            otherThread[0] = CorrelationContext.correlationId();
            started.countDown();
            done.countDown();
        });
        t.start();
        started.await();
        done.await();

        assertNotEquals(first, otherThread[0], "fresh thread must not inherit context through ThreadLocal");
    }

    @Test
    void causationBindsWithoutLosingCorrelation() {
        String correlation = CorrelationContext.correlationId();
        CorrelationContext.setCausation("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(correlation, CorrelationContext.correlationId());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", CorrelationContext.causationId());
    }

    @Test
    void clearRemovesState() {
        CorrelationContext.correlationId();
        CorrelationContext.clear();
        assertNull(CorrelationContext.current());
        String regenerated = CorrelationContext.correlationId();
        assertNotEquals("clear must allow a fresh id", "", regenerated);
    }
}
