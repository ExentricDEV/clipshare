package com.exentricdev.transcoder_worker.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue topology. The worker declares the same queues as the backend (with identical arguments), so either
 * service can start first.
 *
 * Retry policy lives in application.properties (spring.rabbitmq.listener.simple.retry.*): a failed job is retried
 * with exponential backoff, then rejected without requeue, which dead-letters it to {@code transcoder-failures}.
 */
@Configuration
public class RabbitConfig {
    public static final String JOBS = "video-transcoder";
    public static final String FAILURES = "transcoder-failures";
    public static final String PROGRESS = "transcode-progress";
    public static final String PROCESSED = "processed-videos";

    @Bean
    public Queue transcoderQueue() {
        return QueueBuilder.durable(JOBS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", FAILURES)
                .build();
    }

    @Bean
    public Queue transcoderFailuresQueue() {
        return QueueBuilder.durable(FAILURES).build();
    }

    @Bean
    public Queue processedVideosQueue() {
        return QueueBuilder.durable(PROCESSED).build();
    }

    @Bean
    public Queue progressQueue() {
        // progress is disposable: a stale update is useless, so keep at most a few seconds' worth
        return QueueBuilder.durable(PROGRESS).withArgument("x-message-ttl", 10_000).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer(MessageConverter messageConverter) {
        return rabbitTemplate -> rabbitTemplate.setMessageConverter(messageConverter);
    }
}
