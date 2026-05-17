package dev.joaopdias.auronix.config;

public final class RabbitNames {
    public static final String TRANSACTION_EXCHANGE = "auronix.transaction.exchange";

    public static final String TRANSFER_CREATE_QUEUE = "auronix.transfer.create.queue";
    public static final String TRANSFER_CREATE_ROUTING_KEY = "transfer.create";

    public static final String TRANSACTION_COMPLETED_QUEUE = "auronix.transaction.completed.queue";
    public static final String TRANSACTION_COMPLETED_ROUTING_KEY = "transaction.completed";

    public static final String PAYMENT_REQUEST_EXPIRATION_QUEUE = "auronix.payment-request.expiration.queue";
    public static final String PAYMENT_REQUEST_EXPIRATION_DELAY_QUEUE = "auronix.payment-request.expiration.delay.queue";
    public static final String PAYMENT_REQUEST_EXPIRATION_ROUTING_KEY = "payment-request.expiration";
    public static final String PAYMENT_REQUEST_EXPIRATION_DELAY_ROUTING_KEY = "payment-request.expiration.delay";
}
