package com.evplatform.simulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Smoke: the skeleton fails fast instead of pretending to run. */
class ChargerSimulatorTest {

    @Test
    void mainIsNotImplementedYet() {
        assertThrows(UnsupportedOperationException.class,
            () -> ChargerSimulator.main(new String[0]));
    }
}
