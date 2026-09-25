package com.archguard;

import com.archguard.models.AnalysisJob;
import com.archguard.repositories.AnalysisJobRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/jobs")
public class AnalysisController {

    private final RabbitTemplate rabbitTemplate;
    private final AnalysisJobRepository jobRepository;

    public AnalysisController(RabbitTemplate rabbitTemplate, AnalysisJobRepository jobRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.jobRepository = jobRepository;
    }
   

    @PostMapping
    public String submitJob(@RequestBody String repositoryUrl) {
        // 1. Create and save the pending job record in PostgreSQL
        AnalysisJob job = new AnalysisJob(repositoryUrl, "PENDING");
        job = jobRepository.save(job);

        // 2. Publish the job ID to RabbitMQ
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, job.getId().toString());

        return "Job Queued successfully with ID: " + job.getId();
    }
}