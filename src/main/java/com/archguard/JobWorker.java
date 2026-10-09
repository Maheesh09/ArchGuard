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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    /** Directories that never contain the project's own source code. */
    static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".hg", ".svn", ".venv", "venv", "env", ".tox", ".nox",
            "node_modules", "site-packages", "__pycache__", ".mypy_cache", ".pytest_cache");

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

    /**
     * Returning normally acks the message. Bad messages are logged and dropped instead of
     * throwing, because an exception would requeue them and redeliver them forever.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processJob(String jobIdStr) {
        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdStr == null ? "" : jobIdStr.strip());
        } catch (IllegalArgumentException e) {
            log.warn("[WORKER] Discarding message with invalid job ID: '{}'", jobIdStr);
            return;
        }
        log.info("[WORKER] Picked up job ID: {}", jobId);

        // Fetch job from database
        Optional<AnalysisJob> found = jobRepository.findById(jobId);
        if (found.isEmpty()) {
            log.warn("[WORKER] Discarding message: job {} not found in database.", jobId);
            return;
        }
        AnalysisJob job = found.get();

        // Update status to IN_PROGRESS
        job.markInProgress();
        jobRepository.save(job);

        // A redelivered or retried job starts from a clean slate instead of duplicating violations
        int removed = violationRepository.deleteByJobId(jobId);
        if (removed > 0) {
            log.info("[WORKER] Removed {} violations from a previous run of job {}.", removed, jobId);
        }

        Path repoDir = null;
        try {
            repoDir = gitService.workspaceFor(jobId.toString());
            gitService.cloneRepository(job.getRepositoryUrl(), repoDir);

            AnalysisSummary summary = analyse(jobId, repoDir);

            job.markCompleted(summary.filesAnalyzed(), summary.filesSkipped());
            jobRepository.save(job);
            log.info("[WORKER] Job ID: {} completed successfully.", jobId);

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[WORKER] Job {} failed: {}", jobId, e.getMessage());
            job.markFailed(e.getMessage());
            jobRepository.save(job);
        } finally {
            // Always remove the clone, whether the job succeeded or not
            gitService.deleteQuietly(repoDir);
        }
    }

    record AnalysisSummary(int filesAnalyzed, int filesSkipped) {}

    private AnalysisSummary analyse(UUID jobId, Path repoDir) throws IOException, InterruptedException {
        List<Path> pythonFiles = findPythonFiles(repoDir);
        log.info("[WORKER] Found {} Python files to analyze.", pythonFiles.size());

        int skipped = 0;
        for (Path pyFile : pythonFiles) {
            // Rules match on the path inside the repo, never on the temp clone location
            String relativePath = relativize(repoDir, pyFile);

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

            for (RuleEngine.ViolationResult v : engine.evaluateViewBoundary(relativePath, facts.imports())) {
                violationRepository.save(new Violation(jobId, v.filePath(), v.ruleBroken(), v.lineNumber()));
                log.info("[WORKER Saved Violation] File: {} | Line: {} | Rule: {}",
                        v.filePath(), v.lineNumber(), v.ruleBroken());
            }
        }

        if (skipped > 0) {
            log.warn("[WORKER] {} of {} Python files could not be parsed and were skipped.", skipped, pythonFiles.size());
        }
        return new AnalysisSummary(pythonFiles.size() - skipped, skipped);
    }

    /** All .py files under the repo, skipping VCS metadata, virtualenvs and caches. */
    static List<Path> findPythonFiles(Path repoDir) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(repoDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(repoDir) && IGNORED_DIRECTORIES.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".py")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(null);
        return files;
    }

    /** Repo-relative path with '/' separators, e.g. "app/views/profile.py". */
    static String relativize(Path repoRoot, Path file) {
        return repoRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace(File.separatorChar, '/');
    }
}
