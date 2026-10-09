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

            List<Path> pythonFiles;
            try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
                pythonFiles = paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".py"))
                        .toList();
            }
            log.info("[WORKER] Found {} Python files to analyze.", pythonFiles.size());

            int skipped = 0;
            for(Path pyFile : pythonFiles){
                // Rules match on the path inside the repo, never on the temp clone location
                String relativePath = relativize(repoDir.toPath(), pyFile);

                // One unparseable file is skipped; it must not fail the whole job.
                // SidecarUnavailableException (no Python, no script) is not caught here and fails the job.
                PythonFileFacts facts;
                try {
                    facts = executor.parsePythonFile(pyFile.toAbsolutePath());
                } catch (SidecarExecutor.SidecarException e) {
                    skipped++;
                    log.warn("[WORKER] Skipping {}: {}", relativePath, e.getMessage());
                    continue;
                }
                if (facts.hasError()) {
                    skipped++;
                    log.warn("[WORKER] Skipping {}: {}", relativePath, facts.error());
                    continue;
                }

                List<RuleEngine.ViolationResult> violationResults = engine.evaluateViewBoundary(relativePath, facts.imports());

                for (RuleEngine.ViolationResult v : violationResults) {
                    Violation entity = new Violation(jobId, v.filePath(), v.ruleBroken(), v.lineNumber());
                    violationRepository.save(entity);
                    log.info("[WORKER Saved Violation] File: {} | Line: {} | Rule: {}",
                            v.filePath(), v.lineNumber(), v.ruleBroken());
                }
            }
            if (skipped > 0) {
                log.warn("[WORKER] {} of {} Python files could not be parsed and were skipped.", skipped, pythonFiles.size());
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

    /** Repo-relative path with '/' separators, e.g. "app/views/profile.py". */
    static String relativize(Path repoRoot, Path file) {
        return repoRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace(File.separatorChar, '/');
    }
}