package com.archguard.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    public static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String repositoryUrl;

    // Stored by name, so existing rows ("PENDING", "COMPLETED", ...) stay readable
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(length = MAX_ERROR_LENGTH)
    private String errorMessage;

    private Integer filesAnalyzed;
    private Integer filesSkipped;

    private Instant createdAt;
    private Instant updatedAt;

    public AnalysisJob() {}

    public AnalysisJob(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
        this.status = JobStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markInProgress() {
        status = JobStatus.IN_PROGRESS;
        errorMessage = null;
        filesAnalyzed = null;
        filesSkipped = null;
    }

    public void markCompleted(int filesAnalyzed, int filesSkipped) {
        status = JobStatus.COMPLETED;
        this.filesAnalyzed = filesAnalyzed;
        this.filesSkipped = filesSkipped;
    }

    public void markFailed(String errorMessage) {
        status = JobStatus.FAILED;
        String message = errorMessage == null || errorMessage.isBlank() ? "Unknown error" : errorMessage;
        this.errorMessage = message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() { return id; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public JobStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getFilesAnalyzed() { return filesAnalyzed; }
    public Integer getFilesSkipped() { return filesSkipped; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
