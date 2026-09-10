package com.evplatform.governancesupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GovernanceSupport Service entry point.
 *
 * Runway skeleton only: it intentionally contains no business capability.
 * Behavior is delivered by the owning delivery tasks against approved
 * contracts and migrations.
 */
@SpringBootApplication
public class GovernanceSupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovernanceSupportApplication.class, args);
    }
}
