package com.archguard;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String QUEUE_NAME = "analysis_jobs";

    @Bean
    public Queue analysisQueue() {
        return new Queue(QUEUE_NAME, true);
    }
}
