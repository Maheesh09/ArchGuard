package com.archguard;

import com.archguard.api.JobResponse;
import com.archguard.api.ViolationResponse;
import com.archguard.models.AnalysisJob;
import com.archguard.repositories.AnalysisJobRepository;
import com.archguard.repositories.ViolationRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/jobs")
public class AnalysisController {

    private final RabbitTemplate rabbitTemplate;
    private final AnalysisJobRepository jobRepository;
    private final ViolationRepository violationRepository;

    public AnalysisController(RabbitTemplate rabbitTemplate, AnalysisJobRepository jobRepository,
                              ViolationRepository violationRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.jobRepository = jobRepository;
        this.violationRepository = violationRepository;
    }


    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@RequestBody(required = false) String repositoryUrl) {
        // 0. Only public GitHub HTTPS repositories may be cloned
        String cloneUrl;
        try {
            cloneUrl = RepositoryUrlValidator.validate(repositoryUrl);
        } catch (RepositoryUrlValidator.InvalidRepositoryUrlException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // 1. Create and save the pending job record in PostgreSQL
        AnalysisJob job = jobRepository.save(new AnalysisJob(cloneUrl));

        // 2. Publish the job ID to RabbitMQ
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, job.getId().toString());

        // 202: the job is queued; poll the Location URL for its status
        return ResponseEntity
                .accepted()
                .location(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(job.getId()).toUri())
                .body(JobResponse.from(job, 0));
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        AnalysisJob job = findJob(id);
        return JobResponse.from(job, violationRepository.countByJobId(id));
    }

    @GetMapping("/{id}/violations")
    public List<ViolationResponse> getViolations(@PathVariable UUID id) {
        findJob(id); // 404 for unknown jobs rather than an empty list
        return violationRepository.findByJobIdOrderByFilePathAscLineNumberAsc(id).stream()
                .map(ViolationResponse::from)
                .toList();
    }

    private AnalysisJob findJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));
    }
}
