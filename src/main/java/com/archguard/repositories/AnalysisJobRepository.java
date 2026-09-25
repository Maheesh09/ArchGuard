package com.archguard.repositories;

import com.archguard.models.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {
}