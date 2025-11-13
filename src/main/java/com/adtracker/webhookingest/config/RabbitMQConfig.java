package com.adtracker.webhookingest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for message publishing.
 *
 * Configures exchanges, queues, bindings, and message converters
 * for publishing webhook events to RabbitMQ.
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    @Value("${webhook.ingestion.queue.name:youtube.webhooks.raw}")
    private String queueName;

    @Value("${webhook.ingestion.queue.exchange:youtube.webhooks}")
    private String exchangeName;

    @Value("${webhook.ingestion.queue.routing-key:webhook.received}")
    private String routingKey;

    /**
     * Create the webhook queue.
     */
    @Bean
    public Queue webhookQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", 86400000) // 24 hours
                .withArgument("x-max-length", 100000)
                .build();
    }

    /**
     * Create the webhook exchange.
     */
    @Bean
    public TopicExchange webhookExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Bind the queue to the exchange with routing key.
     */
    @Bean
    public Binding webhookBinding(Queue webhookQueue, TopicExchange webhookExchange) {
        return BindingBuilder.bind(webhookQueue)
                .to(webhookExchange)
                .with(routingKey);
    }

    /**
     * Configure JSON message converter for RabbitMQ messages.
     * Uses ObjectMapper to avoid deprecated constructor.
     */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Configure RabbitTemplate with custom message converter.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true); // Return messages if they cannot be routed

        // Configure return callback for unroutable messages
        template.setReturnsCallback(returned -> {
            log.error("Message returned: {} from exchange: {} with routing key: {}",
                    returned.getMessage(),
                    returned.getExchange(),
                    returned.getRoutingKey());
        });

        // Configure confirm callback for publisher confirms
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Message publish failed. Correlation data: {}, Cause: {}",
                        correlationData, cause);
            }
        });

        log.info("RabbitTemplate configured with JSON message converter");
        return template;
    }
}
