package com.archguard;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final Path workspaceRoot;
    private final int timeoutSeconds;

    public GitService(
            @Value("${archguard.git.workspace:${java.io.tmpdir}/archguard/jobs}") String workspaceRoot,
            @Value("${archguard.git.timeout-seconds:120}") int timeoutSeconds) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.timeoutSeconds = timeoutSeconds;
    }

    /** The directory a job's repository is cloned into. */
    public Path workspaceFor(String jobId) {
        Path dir = workspaceRoot.resolve(jobId).normalize();
        if (!dir.startsWith(workspaceRoot) || dir.equals(workspaceRoot)) {
            throw new IllegalArgumentException("Invalid job id for workspace: " + jobId);
        }
        return dir;
    }

    /**
     * Shallow-clones a GitHub repository (latest commit only, no tags, no submodules).
     * The caller owns {@code targetDir} and must delete it with {@link #deleteQuietly(Path)}.
     */
    public void cloneRepository(String repoUrl, Path targetDir) throws Exception {
        String cloneUrl = RepositoryUrlValidator.validate(repoUrl);

        // Start from an empty directory, e.g. when a job is retried
        deleteQuietly(targetDir);
        Files.createDirectories(targetDir);
        log.info("[GIT] Cloning {} into {}", cloneUrl, targetDir);

        Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(targetDir.toFile())
                .setDepth(1)
                .setNoTags()
                .setCloneSubmodules(false)
                .setTimeout(timeoutSeconds)
                .call()
                .close();
    }

    /** Recursively deletes a directory, including read-only git pack files. Never throws. */
    public void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            FileUtils.delete(dir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
        } catch (IOException e) {
            log.warn("[GIT] Could not delete {}: {}", dir, e.getMessage());
        }
    }
}
