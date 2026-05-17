package dev.joaopdias.auronix.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    public DirectExchange transactionExchange() {
        return new DirectExchange(RabbitNames.TRANSACTION_EXCHANGE, true, false);
    }

    @Bean
    public Queue transferCreateQueue() {
        return new Queue(RabbitNames.TRANSFER_CREATE_QUEUE, true);
    }

    @Bean
    public Queue transactionCompletedQueue() {
        return new Queue(RabbitNames.TRANSACTION_COMPLETED_QUEUE, true);
    }

    @Bean
    public Queue paymentRequestExpirationQueue() {
        return new Queue(RabbitNames.PAYMENT_REQUEST_EXPIRATION_QUEUE, true);
    }

    @Bean
    public Queue paymentRequestExpirationDelayQueue() {
        return QueueBuilder
            .durable(RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_QUEUE)
            .ttl(10 * 60 * 1000)
            .deadLetterExchange(RabbitNames.TRANSACTION_EXCHANGE)
            .deadLetterRoutingKey(RabbitNames.PAYMENT_REQUEST_EXPIRATION_ROUTING_KEY)
            .build();
    }

    @Bean
    public Binding transferCreateBinding(
        Queue transferCreateQueue,
        DirectExchange transactionExchange
    ) {
        return BindingBuilder
            .bind(transferCreateQueue)
            .to(transactionExchange)
            .with(RabbitNames.TRANSFER_CREATE_ROUTING_KEY);
    }

    @Bean
    public Binding transactionCompletedBinding(
        Queue transactionCompletedQueue,
        DirectExchange transactionExchange
    ) {
        return BindingBuilder
            .bind(transactionCompletedQueue)
            .to(transactionExchange)
            .with(RabbitNames.TRANSACTION_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentRequestExpirationBinding(
        Queue paymentRequestExpirationQueue,
        DirectExchange transactionExchange
    ) {
        return BindingBuilder
            .bind(paymentRequestExpirationQueue)
            .to(transactionExchange)
            .with(RabbitNames.PAYMENT_REQUEST_EXPIRATION_ROUTING_KEY);
    }

    @Bean
    public Binding paymentRequestExpirationDelayBinding(
        Queue paymentRequestExpirationDelayQueue,
        DirectExchange transactionExchange
    ) {
        return BindingBuilder
            .bind(paymentRequestExpirationDelayQueue)
            .to(transactionExchange)
            .with(RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
