package com.archguard;

import com.archguard.models.AnalysisJob;
import com.archguard.models.Violation;
import com.archguard.repositories.AnalysisJobRepository;
import com.archguard.repositories.ViolationRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JobWorker {

    private final AnalysisJobRepository jobRepository;
    private final ViolationRepository violationRepository;
    private final SidecarExecutor executor = new SidecarExecutor();
    private final RuleEngine engine = new RuleEngine();

    public JobWorker(AnalysisJobRepository jobRepository, ViolationRepository violationRepository) {
        this.jobRepository = jobRepository;
        this.violationRepository = violationRepository;
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
                logger.info("[WORKER Saved Violation] File: %s | Line: %d | Rule: %s%n",
                        v.filePath(), v.lineNumber(), v.ruleBroken());
            }

            // Mark job as COMPLETED
            job.setStatus("COMPLETED");
            jobRepository.save(job);
            log.info("[WORKER] Job completed and saved to PostgreSQL.\n");
            log.info("[WORKER] Job completed and saved to PostgreSQL.\n");

        } catch (Exception e) {
            log.error("[WORKER] Job failed: " + e.getMessage());
            job.setStatus("FAILED");
            jobRepository.save(job);
        }
    }
}