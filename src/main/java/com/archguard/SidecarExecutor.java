package com.archguard;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;

@Component
public class SidecarExecutor {

    public String parsePythonFile(String pythonScriptPath, String targetFilePath) throws Exception {
        String python = isWindows() ? "python" : "python3";
        ProcessBuilder processBuilder = new ProcessBuilder(python, pythonScriptPath, targetFilePath);
        processBuilder.directory(new File(System.getProperty("user.dir")));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Sidecar execution failed with exit code: " + exitCode + ". Output: " + output);
        }

        return output.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}