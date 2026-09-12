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
 * Discovery consumer topology per ENG-001 doc §6.4 and the I1-DSC-001 task
 * packet: one durable topic exchange anchor (idempotent declaration — the
 * exchange already exists on the broker from station-operations-service),
 * one QUORUM queue with a default-exchange dead-letter path, and the
 * station.published binding.
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

    public static final String STATION_PUBLISHED_QUEUE = "discovery.station.published";
    public static final String STATION_PUBLISHED_DLQ = STATION_PUBLISHED_QUEUE + ".dlq";

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
    public Queue stationPublishedDlq() {
        return new Queue(STATION_PUBLISHED_DLQ, true, false, false,
                Map.of("x-queue-type", "quorum"));
    }

    /**
     * QUORUM main queue; rejects with requeue=false dead-letter via the
     * default exchange ("") to the DLQ routing key (same wiring the STA
     * integration harness uses).
     */
    @Bean
    public Queue stationPublishedQueue() {
        return new Queue(STATION_PUBLISHED_QUEUE, true, false, false,
                Map.of("x-queue-type", "quorum",
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", STATION_PUBLISHED_DLQ));
    }

    @Bean
    public Binding stationPublishedBinding(Queue stationPublishedQueue,
                                           TopicExchange domainExchange) {
        return BindingBuilder.bind(stationPublishedQueue)
                .to(domainExchange)
                .with(STATION_PUBLISHED_ROUTING_KEY);
    }
}
