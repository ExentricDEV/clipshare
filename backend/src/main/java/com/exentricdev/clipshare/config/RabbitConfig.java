package com.exentricdev.clipshare.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

/**
 * Queue topology (declared identically by the transcoder worker, so either can start first):
 *
 * <pre>
 *   backend --video-transcoder--> worker            (dead-letters to transcoder-failures)
 *   worker  --transcode-progress--> backend          (~1/s per job, 10s TTL: stale progress is useless)
 *   worker  --processed-videos--> backend            (HLS ladder published)
 *   RabbitMQ --transcoder-failures--> backend        (retries exhausted: mark FAILED)
 * </pre>
 *
 * The listener factory below applies to the backend's own listeners. The worker's retry policy is configured
 * in the worker (spring.rabbitmq.listener.simple.retry.*).
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
        return QueueBuilder.durable(PROGRESS).withArgument("x-message-ttl", 10_000).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setMessageConverter(messageConverter);
        // a message the backend can't handle after retries is dropped (and logged) rather than looping forever
        factory.setDefaultRequeueRejected(false);

        RetryPolicySettings retryPolicySettings = new RetryPolicySettings();
        retryPolicySettings.setMaxRetries(3L);
        retryPolicySettings.setDelay(Duration.ofSeconds(2));
        retryPolicySettings.setMultiplier(2.0);
        retryPolicySettings.setMaxDelay(Duration.ofSeconds(10));

        RetryTemplate retry = new RetryTemplate();
        retry.setRetryPolicy(retryPolicySettings.createRetryPolicy());

        factory.setRetryTemplate(retry);
        return factory;
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
