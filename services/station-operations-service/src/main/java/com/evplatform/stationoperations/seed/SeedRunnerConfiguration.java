package com.evplatform.stationoperations.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Profile-gated entry points for the S1-01 seed (GOV-007 §3).
 *
 * Usage (after migrations have run, e.g. via the migration pipeline):
 * <pre>
 *   java -jar station-operations-service.jar --spring.profiles.active=seed
 *   java -jar station-operations-service.jar --spring.profiles.active=seed-reset
 * </pre>
 *
 * Both runners exit the JVM when finished — the service process itself is
 * not started in seed mode.
 *
 * <p><b>Role requirement:</b> seed-reset must run with the migrator role
 * (STA_DB_USER=station_operations_migrator), not the runtime role. The
 * runtime role is blocked by the audit_event REVOKE (UPDATE/DELETE) from
 * performing the reset's truncation/delete work, and lacks the ALTER TABLE
 * privilege needed to disable triggers during the reset.</p>
 */
@Configuration
public class SeedRunnerConfiguration {

    @Bean
    @Profile("seed")
    public ApplicationRunner seedRunner(StationOperationsSeeder seeder, ApplicationContext context) {
        return (ApplicationArguments args) -> {
            seeder.seed();
            exit(context);
        };
    }

    @Bean
    @Profile("seed-reset")
    public ApplicationRunner resetThenSeedRunner(StationOperationsReset reset,
                                                 StationOperationsSeeder seeder,
                                                 ApplicationContext context) {
        return (ApplicationArguments args) -> {
            reset.reset();
            seeder.seed();
            exit(context);
        };
    }

    private void exit(ApplicationContext context) {
        int code = SpringApplication.exit(context);
        System.exit(code);
    }
}
