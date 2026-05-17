package dev.joaopdias.auronix.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

class RabbitConfigTest {
    private final RabbitConfig rabbitConfig = new RabbitConfig();

    @Test
    void transactionExchangeIsDurableAndNotAutoDelete() {
        DirectExchange exchange = rabbitConfig.transactionExchange();

        assertThat(exchange.getName()).isEqualTo(RabbitNames.TRANSACTION_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    void queuesAreDurable() {
        Queue transferCreateQueue = rabbitConfig.transferCreateQueue();
        Queue transactionCompletedQueue = rabbitConfig.transactionCompletedQueue();
        Queue paymentRequestExpirationQueue = rabbitConfig.paymentRequestExpirationQueue();
        Queue paymentRequestExpirationDelayQueue = rabbitConfig.paymentRequestExpirationDelayQueue();

        assertThat(transferCreateQueue.getName()).isEqualTo(RabbitNames.TRANSFER_CREATE_QUEUE);
        assertThat(transferCreateQueue.isDurable()).isTrue();
        assertThat(transactionCompletedQueue.getName()).isEqualTo(RabbitNames.TRANSACTION_COMPLETED_QUEUE);
        assertThat(transactionCompletedQueue.isDurable()).isTrue();
        assertThat(paymentRequestExpirationQueue.getName()).isEqualTo(RabbitNames.PAYMENT_REQUEST_EXPIRATION_QUEUE);
        assertThat(paymentRequestExpirationQueue.isDurable()).isTrue();
        assertThat(paymentRequestExpirationDelayQueue.getName()).isEqualTo(RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_QUEUE);
        assertThat(paymentRequestExpirationDelayQueue.isDurable()).isTrue();
        assertThat(paymentRequestExpirationDelayQueue.getArguments())
            .containsEntry("x-message-ttl", 600000)
            .containsEntry("x-dead-letter-exchange", RabbitNames.TRANSACTION_EXCHANGE)
            .containsEntry("x-dead-letter-routing-key", RabbitNames.PAYMENT_REQUEST_EXPIRATION_ROUTING_KEY);
    }

    @Test
    void transferCreateBindingUsesConfiguredNames() {
        Queue queue = rabbitConfig.transferCreateQueue();
        DirectExchange exchange = rabbitConfig.transactionExchange();

        Binding binding = rabbitConfig.transferCreateBinding(queue, exchange);

        assertThat(binding.getDestination()).isEqualTo(RabbitNames.TRANSFER_CREATE_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitNames.TRANSACTION_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitNames.TRANSFER_CREATE_ROUTING_KEY);
        assertThat(binding.isDestinationQueue()).isTrue();
    }

    @Test
    void transactionCompletedBindingUsesConfiguredNames() {
        Queue queue = rabbitConfig.transactionCompletedQueue();
        DirectExchange exchange = rabbitConfig.transactionExchange();

        Binding binding = rabbitConfig.transactionCompletedBinding(queue, exchange);

        assertThat(binding.getDestination()).isEqualTo(RabbitNames.TRANSACTION_COMPLETED_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitNames.TRANSACTION_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitNames.TRANSACTION_COMPLETED_ROUTING_KEY);
        assertThat(binding.isDestinationQueue()).isTrue();
    }

    @Test
    void paymentRequestExpirationBindingUsesConfiguredNames() {
        Queue queue = rabbitConfig.paymentRequestExpirationQueue();
        DirectExchange exchange = rabbitConfig.transactionExchange();

        Binding binding = rabbitConfig.paymentRequestExpirationBinding(queue, exchange);

        assertThat(binding.getDestination()).isEqualTo(RabbitNames.PAYMENT_REQUEST_EXPIRATION_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitNames.TRANSACTION_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitNames.PAYMENT_REQUEST_EXPIRATION_ROUTING_KEY);
        assertThat(binding.isDestinationQueue()).isTrue();
    }

    @Test
    void paymentRequestExpirationDelayBindingUsesConfiguredNames() {
        Queue queue = rabbitConfig.paymentRequestExpirationDelayQueue();
        DirectExchange exchange = rabbitConfig.transactionExchange();

        Binding binding = rabbitConfig.paymentRequestExpirationDelayBinding(queue, exchange);

        assertThat(binding.getDestination()).isEqualTo(RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitNames.TRANSACTION_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitNames.PAYMENT_REQUEST_EXPIRATION_DELAY_ROUTING_KEY);
        assertThat(binding.isDestinationQueue()).isTrue();
    }

    @Test
    void messageConverterUsesJacksonJson() {
        MessageConverter converter = rabbitConfig.messageConverter();

        assertThat(converter).isInstanceOf(JacksonJsonMessageConverter.class);
    }
}
