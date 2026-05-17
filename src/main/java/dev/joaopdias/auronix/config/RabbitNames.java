package dev.joaopdias.auronix.config;

public final class RabbitNames {
    public static final String TRANSACTION_EXCHANGE = "auronix.transaction.exchange";

    public static final String TRANSFER_CREATE_QUEUE = "auronix.transfer.create.queue";
    public static final String TRANSFER_CREATE_ROUTING_KEY = "transfer.create";

    public static final String TRANSACTION_COMPLETED_QUEUE = "auronix.transaction.completed.queue";
    public static final String TRANSACTION_COMPLETED_ROUTING_KEY = "transaction.completed";
}