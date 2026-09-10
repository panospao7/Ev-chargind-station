package com.evplatform.stationoperations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StationOperations Service entry point.
 *
 * Runway skeleton only: it intentionally contains no business capability.
 * Behavior is delivered by the owning delivery tasks against approved
 * contracts and migrations.
 */
@SpringBootApplication
public class StationOperationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StationOperationsApplication.class, args);
    }
}
