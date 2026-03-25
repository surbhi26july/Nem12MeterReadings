package com.nem12.repository;

import com.nem12.model.entity.BatchAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BatchAuditLogRepository extends JpaRepository<BatchAuditLog, UUID> {

    /**
     * Returns [totalBatches, readingsPersisted, failedReadings, errorCount] for a single job.
     */
    @Query("SELECT COUNT(b), " +
           "COALESCE(SUM(b.readingsPersisted), 0), " +
           "COALESCE(SUM(CASE WHEN b.status = 'FAILED' THEN b.readingCount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN b.status = 'FAILED' THEN 1 ELSE 0 END), 0) " +
           "FROM BatchAuditLog b WHERE b.jobId = :jobId")
    Object[] getJobStats(UUID jobId);

    /**
     * Bulk stats for multiple jobs in a single query. Each row is [jobId, totalBatches, persisted, failed, errors].
     */
    @Query("SELECT b.jobId, COUNT(b), " +
           "COALESCE(SUM(b.readingsPersisted), 0), " +
           "COALESCE(SUM(CASE WHEN b.status = 'FAILED' THEN b.readingCount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN b.status = 'FAILED' THEN 1 ELSE 0 END), 0) " +
           "FROM BatchAuditLog b WHERE b.jobId IN :jobIds GROUP BY b.jobId")
    List<Object[]> getJobStatsBulk(List<UUID> jobIds);

    Page<BatchAuditLog> findByJobIdAndStatusOrderByBatchNumberAsc(
            UUID jobId, BatchAuditLog.BatchStatus status, Pageable pageable);
}
