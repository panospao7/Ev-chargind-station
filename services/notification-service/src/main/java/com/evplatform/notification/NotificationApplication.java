package com.evplatform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service entry point.
 *
 * Runway skeleton only: it intentionally contains no business capability.
 * Behavior is delivered by the owning delivery tasks against approved
 * contracts and migrations.
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
