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
