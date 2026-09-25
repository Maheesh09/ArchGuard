package com.archguard.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "violations")
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID jobId;
    private String filePath;
    private String ruleBroken;
    private int lineNumber;

    public Violation() {}

    public Violation(UUID jobId, String filePath, String ruleBroken, int lineNumber) {
        this.jobId = jobId;
        this.filePath = filePath;
        this.ruleBroken = ruleBroken;
        this.lineNumber = lineNumber;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getFilePath() { return filePath; }
    public String getRuleBroken() { return ruleBroken; }
    public int getLineNumber() { return lineNumber; }
}