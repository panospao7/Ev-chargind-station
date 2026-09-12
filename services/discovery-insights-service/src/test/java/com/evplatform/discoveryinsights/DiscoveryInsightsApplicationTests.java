package com.evplatform.discoveryinsights;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke: the application context starts without a broker or database.
 *
 * The RabbitMQ listener container is NOT auto-started here
 * (spring.rabbitmq.listener.simple.auto-startup=false): the smoke test runs
 * outside the container-backed ordered suite, and a started container would
 * retry connecting to a dead broker forever (log noise) and could interfere
 * with the shared-container suite. Topology/consumer beans still load —
 * only the container startup is suppressed. The full consumer pipeline is
 * exercised by DiscoverySliceIntegrationTest.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class DiscoveryInsightsApplicationTests {

    @Test
    void contextLoads() {
    }
}
