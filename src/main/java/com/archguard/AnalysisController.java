package com.archguard;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class AnalysisController {

    private final RabbitTemplate rabbitTemplate;

    public AnalysisController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    public String submitJob(@RequestBody String repositoryUrl) {
        // Publish the payload to the queue
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, repositoryUrl);
        return "Job Queued successfully for: " + repositoryUrl;
    }
}