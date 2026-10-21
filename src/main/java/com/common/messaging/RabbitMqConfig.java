package com.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Defines the RabbitMQ components and how they are connected.
@Configuration
public class RabbitMqConfig {
    // The exchange receives messages from producers and routes them to queues.
    public static final String ORDER_EXCHANGE =
            "order.events.exchange";

    // This routing key identifies messages related to successfully paid orders.
    // The DirectExchange uses it to decide which queue should receive the message.
    public static final String ORDER_PAID_ROUTING_KEY =
            "order.paid";
    // This is the name of the queue that receives order-paid events

    public static final String TICKET_ORDER_PAID_QUEUE =
            "ticket.order-paid.queue";

    // A DirectExchange routes messages when the routing key exactly matches
    // the routing key defined in a binding.
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }


    // The "true" argument makes the queue durable, so its definition
    // survives a RabbitMQ broker restart.
    @Bean
    public Queue ticketOrderPaidQueue() {
        return new Queue(
                TICKET_ORDER_PAID_QUEUE,
                true
        );
    }
    // Connects the queue to the exchange using the specified routing key,
    // so messages with a matching key can be routed to this queue.
    @Bean
    public Binding ticketOrderPaidBinding(
            Queue ticketOrderPaidQueue,
            DirectExchange orderExchange
    ) {

        return BindingBuilder
                .bind(ticketOrderPaidQueue)
                .to(orderExchange)
                .with(ORDER_PAID_ROUTING_KEY);
    }
}