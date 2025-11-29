package com.exentricdev.clipshare.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

@Configuration
public class RabbitConfig {
    @Bean
    public Queue transcoderQueue() {
        return QueueBuilder.durable("video-transcoder")
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "transcoder-failures")
                .build();
    }

    @Bean
    public Queue transcoderFailuresQueue() {
        return QueueBuilder.durable("transcoder-failures").build();
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
        factory.setDefaultRequeueRejected(false); // Do not requeue messages on failure, send to DLQ instead

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
