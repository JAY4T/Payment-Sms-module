package com.module.paymentsms.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the broker topology contract.
 *
 * <p>The dead-letter exchange is not referenced by any Java code: it is referenced by an operator
 * running {@code rabbitmqctl set_policy ... '{"dead-letter-exchange":"kiwipay.dlx"}'}. Renaming the
 * constant would not break the build, a test, or even a startup - it would only mean RabbitMQ
 * silently drops every message it cannot dead-letter, months later, invisibly. These assertions
 * make that rename a build failure instead.
 *
 * <p>The same names are declared by the LigiOpen backend's PaymentSmsRabbitConfig, so they are part
 * of an interface between two services and their literal values are the contract.
 */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void brokerNamesArePinned() {
        assertThat(RabbitConfig.DEAD_LETTER_EXCHANGE).isEqualTo("kiwipay.dlx");
        assertThat(RabbitConfig.DEAD_LETTER_QUEUE).isEqualTo("paymentsms.dead-letters");
        assertThat(RabbitConfig.TRANSACTIONS_EXCHANGE).isEqualTo("kiwipay.transactions");
        assertThat(RabbitConfig.AUDIT_QUEUE).isEqualTo("paymentsms.transactions.audit");
    }

    @Test
    void deadLetterExchangeIsDurableFanout() {
        FanoutExchange exchange = config.transactionsDeadLetterExchange();

        assertThat(exchange.getName()).isEqualTo(RabbitConfig.DEAD_LETTER_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
        // Auto-delete would remove the DLX the moment its last binding went away, which is exactly
        // when a poison message needs somewhere to land.
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    void deadLetterQueueIsDurableAndNeverExpires() {
        Queue queue = config.deadLetterQueue();

        assertThat(queue.getName()).isEqualTo(RabbitConfig.DEAD_LETTER_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isAutoDelete()).isFalse();
        // No TTL and no max-length. Unlike the audit queue, a dead-lettered message is evidence of
        // a bug: expiring it is how it gets lost a second time.
        assertThat(queue.getArguments()).doesNotContainKey("x-message-ttl");
        assertThat(queue.getArguments()).doesNotContainKey("x-max-length");
    }

    @Test
    void deadLetterQueueIsBoundToTheDeadLetterExchange() {
        FanoutExchange exchange = config.transactionsDeadLetterExchange();
        Queue queue = config.deadLetterQueue();
        Binding binding = config.deadLetterBinding(queue, exchange);

        assertThat(binding.getExchange()).isEqualTo(exchange.getName());
        assertThat(binding.getDestination()).isEqualTo(queue.getName());
    }

    @Test
    void transactionsExchangeIsStillDurableTopic() {
        TopicExchange exchange = config.transactionsExchange();

        assertThat(exchange.getName()).isEqualTo(RabbitConfig.TRANSACTIONS_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    void auditQueueCatchesEveryRoutingKeyButStillExpires() {
        Queue queue = config.transactionsAuditQueue();

        assertThat(queue.getName()).isEqualTo(RabbitConfig.AUDIT_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).containsKey("x-message-ttl");

        Binding binding = config.transactionsAuditBinding(queue, config.transactionsExchange());
        assertThat(binding.getRoutingKey()).isEqualTo("#");
    }
}
