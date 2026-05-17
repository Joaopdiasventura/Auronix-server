package dev.joaopdias.auronix.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
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
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}