package com.archguard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;

public class SidecarExecutor {

    public String parsePythonFile(String pythonScriptPath, String targetFilePath) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("python3", pythonScriptPath, targetFilePath);
        processBuilder.directory(new File(System.getProperty("user.dir")));
        
        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Sidecar execution failed with exit code: " + exitCode);
        }

        return output.toString();
    }
}