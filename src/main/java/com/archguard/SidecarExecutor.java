package com.archguard;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class SidecarExecutor {

    private static final Logger log = LoggerFactory.getLogger(SidecarExecutor.class);
    private static final int MAX_ERROR_OUTPUT = 2_000;

    /** The sidecar ran but could not analyse this one file (timeout, crash, unreadable output). */
    public static class SidecarException extends IOException {
        public SidecarException(String message) {
            super(message);
        }
    }

    /** The sidecar cannot run at all (no interpreter, missing script); every file would fail the same way. */
    public static class SidecarUnavailableException extends IOException {
        public SidecarUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final String pythonExecutable;
    private final Path scriptPath;
    private final Duration timeout;

    public SidecarExecutor(
            @Value("${archguard.sidecar.python.executable:}") String pythonExecutable,
            @Value("${archguard.sidecar.python.script:sidecars/python/parser.py}") String scriptPath,
            @Value("${archguard.sidecar.timeout-seconds:30}") long timeoutSeconds) {
        this.pythonExecutable = pythonExecutable == null || pythonExecutable.isBlank()
                ? defaultPythonExecutable() : pythonExecutable.trim();
        // A relative script path is resolved once, against the working directory at startup
        this.scriptPath = Path.of(scriptPath).toAbsolutePath().normalize();
        this.timeout = Duration.ofSeconds(timeoutSeconds);

        if (!Files.isRegularFile(this.scriptPath)) {
            log.warn("[SIDECAR] Python sidecar script not found at {}. Set archguard.sidecar.python.script.", this.scriptPath);
        }
    }

    public PythonFileFacts parsePythonFile(Path targetFile) throws IOException, InterruptedException {
        if (!Files.isRegularFile(scriptPath)) {
            throw new SidecarUnavailableException("Python sidecar script not found: " + scriptPath, null);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scriptPath.toString(), targetFile.toString());
        processBuilder.directory(scriptPath.getParent().toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONDONTWRITEBYTECODE", "1");

        // stdout carries only the JSON result; stderr (warnings, tracebacks) is kept separate.
        // Redirecting to files means neither pipe can fill up and block the process.
        File stdoutFile = File.createTempFile("archguard-sidecar-", ".out");
        File stderrFile = File.createTempFile("archguard-sidecar-", ".err");
        processBuilder.redirectOutput(stdoutFile);
        processBuilder.redirectError(stderrFile);

        try {
            Process process;
            try {
                process = processBuilder.start();
            } catch (IOException e) {
                throw new SidecarUnavailableException("Could not start Python sidecar with '" + pythonExecutable + "': " + e.getMessage(), e);
            }

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new SidecarException("Sidecar timed out after " + timeout.toSeconds() + "s on " + targetFile);
            }

            String stdout = Files.readString(stdoutFile.toPath(), StandardCharsets.UTF_8);
            String stderr = Files.readString(stderrFile.toPath(), StandardCharsets.UTF_8);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new SidecarException("Sidecar exited with code " + exitCode + " on " + targetFile + ": " + truncate(stderr));
            }
            if (!stderr.isBlank()) {
                log.debug("[SIDECAR] stderr for {}: {}", targetFile, truncate(stderr));
            }

            try {
                return PythonFileFacts.fromJson(stdout);
            } catch (JsonProcessingException e) {
                throw new SidecarException("Sidecar returned invalid JSON for " + targetFile + ": " + truncate(stdout));
            }
        } finally {
            Files.deleteIfExists(stdoutFile.toPath());
            Files.deleteIfExists(stderrFile.toPath());
        }
    }

    private static String truncate(String text) {
        String trimmed = text.strip();
        return trimmed.length() <= MAX_ERROR_OUTPUT ? trimmed : trimmed.substring(0, MAX_ERROR_OUTPUT) + "...";
    }

    private static String defaultPythonExecutable() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
    }
}
