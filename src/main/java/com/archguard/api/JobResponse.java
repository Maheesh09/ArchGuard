package com.archguard.api;

import com.archguard.models.AnalysisJob;
import com.archguard.models.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String repositoryUrl,
        JobStatus status,
        String errorMessage,
        Integer filesAnalyzed,
        Integer filesSkipped,
        long violationCount,
        Instant createdAt,
        Instant updatedAt) {

    public static JobResponse from(AnalysisJob job, long violationCount) {
        return new JobResponse(job.getId(), job.getRepositoryUrl(), job.getStatus(), job.getErrorMessage(),
                job.getFilesAnalyzed(), job.getFilesSkipped(), violationCount, job.getCreatedAt(), job.getUpdatedAt());
    }
}
