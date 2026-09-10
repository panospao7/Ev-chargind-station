package com.evplatform.bookingsession;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BookingSession Service entry point.
 *
 * Runway skeleton only: it intentionally contains no business capability.
 * Behavior is delivered by the owning delivery tasks against approved
 * contracts and migrations.
 */
@SpringBootApplication
public class BookingSessionApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingSessionApplication.class, args);
    }
}
