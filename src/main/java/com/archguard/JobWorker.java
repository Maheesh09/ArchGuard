package com.archguard;

import com.archguard.models.AnalysisJob;
import com.archguard.models.Violation;
import com.archguard.repositories.AnalysisJobRepository;
import com.archguard.repositories.ViolationRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private final AnalysisJobRepository jobRepository;
    private final ViolationRepository violationRepository;
    private final SidecarExecutor executor;
    private final RuleEngine engine;

    public JobWorker(AnalysisJobRepository jobRepository, ViolationRepository violationRepository,
                     SidecarExecutor executor, RuleEngine engine) {
        this.jobRepository = jobRepository;
        this.violationRepository = violationRepository;
        this.executor = executor;
        this.engine = engine;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processJob(String jobIdStr) {
        UUID jobId = UUID.fromString(jobIdStr);
        log.info("\n[WORKER] Picked up job ID: " + jobId);

        // Fetch job from database
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found in database: " + jobId));

        // Update status to IN_PROGRESS
        job.setStatus("IN_PROGRESS");
        jobRepository.save(job);

        try {
            String pythonScript = "sidecars/python/parser.py";
            String testFile = "src/test/resources/views/test_view.py";

            log.info("[WORKER] Executing Python sidecar...");
            String astResult = executor.parsePythonFile(pythonScript, testFile);

            log.info("[WORKER] Evaluating architectural boundaries...");
            List<RuleEngine.ViolationResult> violationResults = engine.evaluateViewBoundary(astResult);

            // Persist all found violations into PostgreSQL
            for (RuleEngine.ViolationResult v : violationResults) {
                Violation entity = new Violation(jobId, v.filePath(), v.ruleBroken(), v.lineNumber());
                violationRepository.save(entity);
                log.info("[WORKER Saved Violation] File: {} | Line: {} | Rule: {}",
                        v.filePath(), v.lineNumber(), v.ruleBroken());
            }

            // Mark job as COMPLETED
            job.setStatus("COMPLETED");
            jobRepository.save(job);
            log.info("[WORKER] Job completed and saved to PostgreSQL.\n");

        } catch (Exception e) {
            log.error("[WORKER] Job failed: " + e.getMessage());
            job.setStatus("FAILED");
            jobRepository.save(job);
        }
    }
}