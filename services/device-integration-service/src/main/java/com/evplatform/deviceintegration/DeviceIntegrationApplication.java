package com.evplatform.deviceintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DeviceIntegration Service entry point.
 *
 * Runway skeleton only: it intentionally contains no business capability.
 * Behavior is delivered by the owning delivery tasks against approved
 * contracts and migrations.
 */
@SpringBootApplication
public class DeviceIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceIntegrationApplication.class, args);
    }
}
