package com.zwskchat.zwskchat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.config.properties.ExportProperties;
import com.zwskchat.zwskchat.config.properties.IngestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ topology + retry policy for the async document ingest pipeline (M1.1).
 *
 * Topology:
 *   - direct exchange  zwsk.ingest.exchange
 *   - main queue       zwsk.ingest.queue   (DLX -> zwsk.ingest.dlx)
 *   - DLX              zwsk.ingest.dlx
 *   - DLQ              zwsk.ingest.dlq
 *
 * Retry strategy:
 *   - Spring Retry advice (RetryTemplate) wraps the listener.
 *   - On exhaustion, message is requeued=false and routed to DLX.
 *   - DlqListener finalises the document state to FAILED.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    private final IngestProperties ingestProps;
    private final ExportProperties exportProps;

    // -------- Exchanges --------
    @Bean
    public DirectExchange ingestExchange() {
        return new DirectExchange(ingestProps.getExchange(), true, false);
    }

    @Bean
    public DirectExchange ingestDlxExchange() {
        return new DirectExchange(ingestProps.getDlqExchange(), true, false);
    }

    @Bean
    public DirectExchange exportExchange() {
        return new DirectExchange(exportProps.getExchange(), true, false);
    }

    @Bean
    public DirectExchange exportDlxExchange() {
        return new DirectExchange(exportProps.getDlqExchange(), true, false);
    }

    // -------- Queues --------
    @Bean
    public Queue ingestQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ingestProps.getDlqExchange());
        args.put("x-dead-letter-routing-key", ingestProps.getDlqRoutingKey());
        return QueueBuilder.durable(ingestProps.getQueue())
                .withArguments(args)
                .build();
    }

    @Bean
    public Queue ingestDlq() {
        return QueueBuilder.durable(ingestProps.getDlq()).build();
    }

    @Bean
    public Queue exportQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", exportProps.getDlqExchange());
        args.put("x-dead-letter-routing-key", exportProps.getDlqRoutingKey());
        return QueueBuilder.durable(exportProps.getQueue())
                .withArguments(args)
                .build();
    }

    @Bean
    public Queue exportDlq() {
        return QueueBuilder.durable(exportProps.getDlq()).build();
    }

    // -------- Bindings --------
    @Bean
    public Binding ingestBinding(Queue ingestQueue, DirectExchange ingestExchange) {
        return BindingBuilder.bind(ingestQueue).to(ingestExchange).with(ingestProps.getRoutingKey());
    }

    @Bean
    public Binding ingestDlqBinding(Queue ingestDlq, DirectExchange ingestDlxExchange) {
        return BindingBuilder.bind(ingestDlq).to(ingestDlxExchange).with(ingestProps.getDlqRoutingKey());
    }

    @Bean
    public Binding exportBinding(Queue exportQueue, DirectExchange exportExchange) {
        return BindingBuilder.bind(exportQueue).to(exportExchange).with(exportProps.getRoutingKey());
    }

    @Bean
    public Binding exportDlqBinding(Queue exportDlq, DirectExchange exportDlxExchange) {
        return BindingBuilder.bind(exportDlq).to(exportDlxExchange).with(exportProps.getDlqRoutingKey());
    }

    // -------- JSON message converter --------
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // -------- RabbitTemplate (publisher) --------
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(converter);
        // Surface broker errors instead of silently swallowing
        tpl.setMandatory(true);
        tpl.setReturnsCallback(returned -> log.error(
                "[Rabbit] returned message: replyText={} exchange={} routingKey={}",
                returned.getReplyText(), returned.getExchange(), returned.getRoutingKey()));
        return tpl;
    }

    // -------- Listener container factory with retry --------
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MessageConverter converter) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, cf);
        f.setMessageConverter(converter);
        f.setDefaultRequeueRejected(false);  // exhaustions go to DLX, not back to head
        f.setRetryTemplate(buildRetryTemplate());
        return f;
    }

    private RetryTemplate buildRetryTemplate() {
        var props = ingestProps.getRetry();
        RetryTemplate tpl = new RetryTemplate();

        SimpleRetryPolicy retry = new SimpleRetryPolicy(Math.max(1, props.getMaxAttempts()));
        tpl.setRetryPolicy(retry);

        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(props.getInitialIntervalMs());
        backoff.setMultiplier(props.getMultiplier());
        backoff.setMaxInterval(props.getMaxIntervalMs());
        tpl.setBackOffPolicy(backoff);

        return tpl;
    }
}
