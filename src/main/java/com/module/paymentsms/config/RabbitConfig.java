package com.module.paymentsms.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// One topic exchange carries every transaction lifecycle event this service publishes
// (transaction.completed / transaction.failed / transaction.cleared, see
// IntasendTransactionServiceImpl.publishTransactionEvent). Consumers bind their own queue to
// whichever routing keys they care about - this side only ever publishes, it never declares or
// binds a queue itself.
@Configuration
public class RabbitConfig {

    public static final String TRANSACTIONS_EXCHANGE = "kiwipay.transactions";

    // Explicit, rather than relying on Spring Boot to auto-provide one - supplying our own
    // RabbitTemplate bean below means Boot no longer creates the default RabbitAdmin either,
    // and without a RabbitAdmin nothing ever declares transactionsExchange() on the broker.
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public TopicExchange transactionsExchange() {
        return new TopicExchange(TRANSACTIONS_EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
