package com.archguard;

import com.archguard.models.AnalysisJob;
import com.archguard.models.JobStatus;
import com.archguard.models.Violation;
import com.archguard.repositories.AnalysisJobRepository;
import com.archguard.repositories.ViolationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobWorkerTest {

    @TempDir
    Path tmp;

    private final AnalysisJobRepository jobRepository = mock(AnalysisJobRepository.class);
    private final ViolationRepository violationRepository = mock(ViolationRepository.class);
    private final SidecarExecutor executor = mock(SidecarExecutor.class);
    private final GitService gitService = mock(GitService.class);
    private final JobWorker worker = new JobWorker(jobRepository, violationRepository, executor, new RuleEngine(), gitService);

    private final UUID jobId = UUID.randomUUID();
    private final AnalysisJob job = new AnalysisJob("https://github.com/owner/repo.git");
    private Path repoDir;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = tmp.resolve(jobId.toString());
        Files.createDirectories(repoDir);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(gitService.workspaceFor(jobId.toString())).thenReturn(repoDir);
    }

    private Path writeFile(String relative) throws IOException {
        Path file = repoDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        return file;
    }

    private static PythonFileFacts facts(ImportStatement... imports) {
        return new PythonFileFacts("f.py", List.of(imports), null);
    }

    @Test
    void savesViolationsWithRepoRelativePathsAndCleansUp() throws Exception {
        Path view = writeFile("app/views/profile.py");
        Path service = writeFile("app/services/user.py");
        when(executor.parsePythonFile(view.toAbsolutePath()))
                .thenReturn(facts(new ImportStatement("sqlalchemy", List.of(), 0, 2)));
        when(executor.parsePythonFile(service.toAbsolutePath()))
                .thenReturn(facts(new ImportStatement("sqlalchemy", List.of(), 0, 1)));

        worker.processJob(jobId.toString());

        ArgumentCaptor<Violation> saved = ArgumentCaptor.forClass(Violation.class);
        verify(violationRepository).save(saved.capture());
        assertThat(saved.getValue().getFilePath()).isEqualTo("app/views/profile.py");
        assertThat(saved.getValue().getLineNumber()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(gitService).deleteQuietly(repoDir);
    }

    @Test
    void skipsFilesThatFailToParseAndStillCompletes() throws Exception {
        Path broken = writeFile("views/broken.py");
        Path hung = writeFile("views/hung.py");
        Path good = writeFile("views/good.py");
        when(executor.parsePythonFile(broken.toAbsolutePath()))
                .thenReturn(new PythonFileFacts("broken.py", List.of(), "SyntaxError: invalid syntax (line 1)"));
        when(executor.parsePythonFile(hung.toAbsolutePath()))
                .thenThrow(new SidecarExecutor.SidecarException("timed out"));
        when(executor.parsePythonFile(good.toAbsolutePath()))
                .thenReturn(facts(new ImportStatement("sqlite3", List.of(), 0, 1)));

        worker.processJob(jobId.toString());

        verify(violationRepository).save(any(Violation.class));
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getFilesAnalyzed()).isEqualTo(1);
        assertThat(job.getFilesSkipped()).isEqualTo(2);
    }

    @Test
    void failsTheJobWhenTheSidecarCannotRunAtAll() throws Exception {
        writeFile("views/a.py");
        when(executor.parsePythonFile(any())).thenThrow(new SidecarExecutor.SidecarUnavailableException("no python", null));

        worker.processJob(jobId.toString());

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(gitService).deleteQuietly(repoDir);
    }

    @Test
    void cleansUpTheCloneWhenCloningFails() throws Exception {
        doThrow(new RuntimeException("network down")).when(gitService).cloneRepository(any(), eq(repoDir));

        worker.processJob(jobId.toString());

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("network down");
        verify(gitService).deleteQuietly(repoDir);
        verify(violationRepository, never()).save(any());
    }

    @Test
    void deletesViolationsFromAPreviousRunBeforeAnalysing() throws Exception {
        worker.processJob(jobId.toString());

        verify(violationRepository).deleteByJobId(jobId);
    }

    @Test
    void unknownJobIdIsAckedWithoutThrowing() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(jobRepository.findById(unknown)).thenReturn(Optional.empty());

        worker.processJob(unknown.toString()); // must not throw, or RabbitMQ would redeliver it

        verify(gitService, never()).cloneRepository(any(), any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void malformedMessageIsAckedWithoutThrowing() throws Exception {
        worker.processJob("not-a-uuid");
        worker.processJob(null);

        verify(jobRepository, never()).findById(any());
        verify(gitService, never()).cloneRepository(any(), any());
    }

    @Test
    void findPythonFilesSkipsVirtualenvsGitAndCaches() throws Exception {
        writeFile("app/main.py");
        writeFile("setup.py");
        writeFile("app/readme.md");
        writeFile(".venv/lib/site.py");
        writeFile("venv/lib/x.py");
        writeFile(".git/hooks/hook.py");
        writeFile("node_modules/pkg/y.py");
        writeFile("app/__pycache__/main.py");

        List<String> found = JobWorker.findPythonFiles(repoDir).stream()
                .map(p -> JobWorker.relativize(repoDir, p))
                .toList();

        assertThat(found).containsExactly("app/main.py", "setup.py");
    }
}
