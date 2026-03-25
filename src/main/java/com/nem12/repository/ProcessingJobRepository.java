package com.nem12.repository;

import com.nem12.model.entity.ProcessingJob;
import com.nem12.model.enums.JobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    List<ProcessingJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Finds jobs that have been stuck in PROCESSING with no recent progress.
     * Used by the stale job detector to mark them as FAILED.
     */
    List<ProcessingJob> findByStatusAndLastUpdatedAtBefore(JobStatus status, LocalDateTime cutoff);
}
