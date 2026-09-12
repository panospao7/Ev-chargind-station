package com.evplatform.discoveryinsights.consumer;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Discovery consumer topology per ENG-001 doc §6.4 and the I1-DSC-002 task
 * packet: one durable topic exchange anchor (idempotent declaration — the
 * exchange already exists on the broker from station-operations-service),
 * one QUORUM queue with a default-exchange dead-letter path, and the four
 * station-domain bindings (station.published, station.evse-configuration-changed,
 * station.connector-configuration-changed, station.tariff-published).
 *
 * Beans are declared LAZILY by RabbitAdmin on first connection — the
 * application context must start (and the context smoke test must pass)
 * without a broker running (mirrors station-operations-service
 * RabbitTopologyConfiguration).
 *
 * No Jackson2JsonMessageConverter: messages are plain JSON strings; the
 * consumer parses them manually (task packet decision).
 */
@Configuration
public class RabbitTopologyConfiguration {

    public static final String DOMAIN_EXCHANGE = "ev.domain.v1";
    public static final String STATION_PUBLISHED_ROUTING_KEY = "station.published";
    public static final String EVSE_CONFIGURATION_CHANGED_ROUTING_KEY =
            "station.evse-configuration-changed";
    public static final String CONNECTOR_CONFIGURATION_CHANGED_ROUTING_KEY =
            "station.connector-configuration-changed";
    public static final String TARIFF_PUBLISHED_ROUTING_KEY = "station.tariff-published";

    public static final String STATION_DOMAIN_QUEUE = "discovery.sta.domain";
    public static final String STATION_DOMAIN_DLQ = STATION_DOMAIN_QUEUE + ".dlq";

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public TopicExchange domainExchange() {
        return new TopicExchange(DOMAIN_EXCHANGE, true, false);
    }

    /** QUORUM dead-letter queue (ENG-001 doc §6.4). */
    @Bean
    public Queue stationDomainDlq() {
        return new Queue(STATION_DOMAIN_DLQ, true, false, false,
                Map.of("x-queue-type", "quorum"));
    }

    /**
     * QUORUM main queue; rejects with requeue=false dead-letter via the
     * default exchange ("") to the DLQ routing key (same wiring the STA
     * integration harness uses).
     */
    @Bean
    public Queue stationDomainQueue() {
        return new Queue(STATION_DOMAIN_QUEUE, true, false, false,
                Map.of("x-queue-type", "quorum",
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", STATION_DOMAIN_DLQ));
    }

    @Bean
    public Binding stationPublishedBinding(Queue stationDomainQueue,
                                           TopicExchange domainExchange) {
        return BindingBuilder.bind(stationDomainQueue)
                .to(domainExchange)
                .with(STATION_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public Binding evseConfigurationChangedBinding(Queue stationDomainQueue,
                                                   TopicExchange domainExchange) {
        return BindingBuilder.bind(stationDomainQueue)
                .to(domainExchange)
                .with(EVSE_CONFIGURATION_CHANGED_ROUTING_KEY);
    }

    @Bean
    public Binding connectorConfigurationChangedBinding(Queue stationDomainQueue,
                                                        TopicExchange domainExchange) {
        return BindingBuilder.bind(stationDomainQueue)
                .to(domainExchange)
                .with(CONNECTOR_CONFIGURATION_CHANGED_ROUTING_KEY);
    }

    @Bean
    public Binding tariffPublishedBinding(Queue stationDomainQueue,
                                          TopicExchange domainExchange) {
        return BindingBuilder.bind(stationDomainQueue)
                .to(domainExchange)
                .with(TARIFF_PUBLISHED_ROUTING_KEY);
    }
}
