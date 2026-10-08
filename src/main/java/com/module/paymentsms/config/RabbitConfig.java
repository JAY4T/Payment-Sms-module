package com.module.paymentsms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// One topic exchange carries every transaction lifecycle event this service publishes
// (transaction.completed / transaction.failed / transaction.cleared, see
// IntasendTransactionServiceImpl.enqueueTransactionEvent). Consumers bind their own queue to
// whichever routing keys they care about.
//
// The publisher additionally declares one queue of its own, which it deliberately does NOT do for
// dead-lettering:
//
//   transactions.audit - bound to "#", so an event is never published to the exchange and dropped
//                        for want of a matching binding (a fresh environment, or a new routing key
//                        family nobody has subscribed to yet). Messages expire after
//                        AUDIT_QUEUE_TTL_MS, so it is a reconciliation backstop rather than a
//                        second unbounded copy of the ledger.
//
// Dead-lettering is intentionally a broker policy rather than queue arguments here. Queue
// arguments are immutable once a queue exists, so adding x-dead-letter-exchange to a queue some
// other service already declared fails with PRECONDITION_FAILED and takes the channel down. The
// DLX and its queue ARE declared below, so applying the policy is enough and a poison message
// lands somewhere real:
//
//   rabbitmqctl set_policy ligiopen-dlx "^(shop\.paymentsms-transactions|paymentsms\..*)$" \
//     '{"dead-letter-exchange":"kiwipay.dlx"}' --apply-to queues
//
// Without those beans the policy would point at an exchange that doesn't exist, and RabbitMQ
// silently drops a message it cannot dead-letter - the exact loss this is meant to prevent.
@Configuration
public class RabbitConfig {

    public static final String TRANSACTIONS_EXCHANGE = "kiwipay.transactions";
    public static final String AUDIT_QUEUE = "paymentsms.transactions.audit";

    // Target of the dead-letter policy above. A fanout is enough: a dead-lettered message carries
    // its original routing key in x-death, and the operator reading the DLQ wants the message, not
    // a routing decision. Declared by every service that participates in the policy so the DLX
    // exists whichever one starts first.
    public static final String DEAD_LETTER_EXCHANGE = "kiwipay.dlx";
    public static final String DEAD_LETTER_QUEUE = "paymentsms.dead-letters";

    // 24h: long enough to notice and replay an outage nobody watched overnight, short enough that
    // the queue can't become an unbounded second copy of the ledger.
    private static final int AUDIT_QUEUE_TTL_MS = 24 * 60 * 60 * 1000;

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

    // Durable, topic-bound catch-all. See the class comment: it exists so an event can never be
    // published to the exchange and then dropped because nothing was bound for its routing key.
    @Bean
    public Queue transactionsAuditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .ttl(AUDIT_QUEUE_TTL_MS)
                .build();
    }

    @Bean
    public Binding transactionsAuditBinding(Queue transactionsAuditQueue, TopicExchange transactionsExchange) {
        return BindingBuilder.bind(transactionsAuditQueue).to(transactionsExchange).with("#");
    }

    @Bean
    public FanoutExchange transactionsDeadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    // No TTL and no max-length: a poison message is evidence, and expiring it is how it gets lost
    // a second time. Rejecting a message into here is the point at which a human should look.
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange transactionsDeadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(transactionsDeadLetterExchange);
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
