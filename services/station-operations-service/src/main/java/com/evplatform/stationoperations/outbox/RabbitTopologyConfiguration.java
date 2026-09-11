package com.evplatform.stationoperations.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * RabbitMQ topology and publisher configuration per ENG-001 doc §6.4: one
 * local vhost, durable topic exchanges from the registry anchors, publisher
 * confirms, mandatory publishing.
 *
 * Exchanges are registered as beans and declared by RabbitAdmin lazily on
 * first connection — the application context must start (and the context
 * smoke test must pass) without a broker running.
 */
@Configuration
@EnableScheduling
public class RabbitTopologyConfiguration {

    public static final String DOMAIN_EXCHANGE = "ev.domain.v1";

    /** Registry anchor exchanges (messages-v1.yaml). */
    public static final List<String> EXCHANGES = List.of(
            "ev.domain.v1", "ev.device.result.v1", "ev.device.telemetry.v1",
            "ev.device.command.v1", "com.evplatform.command");

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public TopicExchange domainExchange() {
        return new TopicExchange("ev.domain.v1", true, false);
    }

    @Bean
    public TopicExchange deviceResultExchange() {
        return new TopicExchange("ev.device.result.v1", true, false);
    }

    @Bean
    public TopicExchange deviceTelemetryExchange() {
        return new TopicExchange("ev.device.telemetry.v1", true, false);
    }

    @Bean
    public TopicExchange deviceCommandExchange() {
        return new TopicExchange("ev.device.command.v1", true, false);
    }

    @Bean
    public TopicExchange commandExchange() {
        return new TopicExchange("com.evplatform.command", true, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setExchange(DOMAIN_EXCHANGE);
        template.setMandatory(true);
        return template;
    }
}
