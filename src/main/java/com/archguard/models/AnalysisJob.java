package com.archguard.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String repositoryUrl;
    
    // Status can be: PENDING, IN_PROGRESS, COMPLETED, FAILED
    private String status;

    public AnalysisJob() {}

    public AnalysisJob(String repositoryUrl, String status) {
        this.repositoryUrl = repositoryUrl;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}