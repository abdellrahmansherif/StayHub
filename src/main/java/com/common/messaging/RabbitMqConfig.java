package com.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EXCHANGE =
            "order.events.exchange";

    public static final String ORDER_PAID_ROUTING_KEY =
            "order.paid";

    public static final String TICKET_ORDER_PAID_QUEUE =
            "ticket.order-paid.queue";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue ticketOrderPaidQueue() {
        return new Queue(
                TICKET_ORDER_PAID_QUEUE,
                true
        );
    }

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