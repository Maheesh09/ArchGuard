package com.archguard;

import com.archguard.models.AnalysisJob;
import com.archguard.models.Violation;
import com.archguard.repositories.AnalysisJobRepository;
import com.archguard.repositories.ViolationRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import java.util.UUID;

@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private final AnalysisJobRepository jobRepository;
    private final ViolationRepository violationRepository;
    private final SidecarExecutor executor;
    private final RuleEngine engine;
    private final GitService gitService;

    public JobWorker(AnalysisJobRepository jobRepository, ViolationRepository violationRepository,
                     SidecarExecutor executor, RuleEngine engine, GitService gitService) {
        this.jobRepository = jobRepository;
        this.violationRepository = violationRepository;
        this.executor = executor;
        this.engine = engine;
        this.gitService = gitService;
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
            File repoDir = gitService.cloneRepository(job.getRepositoryUrl(), jobId.toString());
            String pythonScriptPath = "sidecars/python/parser.py";

            List<Path> pythonFiles;
            try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
                pythonFiles = paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".py"))
                        .toList();
            }
            log.info("[WORKER] Found {} Python files to analyze.", pythonFiles.size());

            for(Path pyFile : pythonFiles){
                String fileAbsolutePath = pyFile.toAbsolutePath().toString();

                if(fileAbsolutePath.contains("sidecars/python")) continue; // Skip the sidecar script itself

                String astResult = executor.parsePythonFile(pythonScriptPath, fileAbsolutePath);
                List<RuleEngine.ViolationResult> violationResults = engine.evaluateViewBoundary(astResult);

                for (RuleEngine.ViolationResult v : violationResults) {
                    // Convert absolute path back to relative path for cleaner database storage
                    String relativePath = fileAbsolutePath.replace(repoDir.getAbsolutePath() + File.separator, "");
                    
                    Violation entity = new Violation(jobId, relativePath, v.ruleBroken(), v.lineNumber());
                    violationRepository.save(entity);
                    log.info("[WORKER Saved Violation] File: {} | Line: {} | Rule: {}",
                            relativePath, v.lineNumber(), v.ruleBroken());
                }
            }
            gitService.deleteDirectory(repoDir); // Clean up the cloned repository
            job.setStatus("COMPLETED");
            jobRepository.save(job);
            log.info("[WORKER] Job ID: " + jobId + " completed successfully.");

        } catch (Exception e) {
            log.error("[WORKER] Job failed: " + e.getMessage());
            job.setStatus("FAILED");
            jobRepository.save(job);
        }
    }
}