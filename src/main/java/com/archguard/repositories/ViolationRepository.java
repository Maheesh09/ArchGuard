package com.archguard.repositories;

import com.archguard.models.Violation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {

    List<Violation> findByJobIdOrderByFilePathAscLineNumberAsc(UUID jobId);

    long countByJobId(UUID jobId);

    /** Removes a job's previous results so a retried job does not duplicate them. */
    @Transactional
    @Modifying
    @Query("delete from Violation v where v.jobId = :jobId")
    int deleteByJobId(@Param("jobId") UUID jobId);
}
