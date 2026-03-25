package com.nem12.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "batch_audit_log")
public class BatchAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    @Column(name = "reading_count", nullable = false)
    private int readingCount;

    @Column(name = "readings_persisted", nullable = false)
    private int readingsPersisted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BatchStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BatchAuditLog() {}

    private BatchAuditLog(UUID jobId, int batchNumber, int readingCount,
                          int readingsPersisted, BatchStatus status, String errorMessage) {
        this.jobId = jobId;
        this.batchNumber = batchNumber;
        this.readingCount = readingCount;
        this.readingsPersisted = readingsPersisted;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = LocalDateTime.now();
    }

    public static BatchAuditLog success(UUID jobId, int batchNumber, int readingCount, int persisted) {
        return new BatchAuditLog(jobId, batchNumber, readingCount, persisted, BatchStatus.SUCCESS, null);
    }

    public static BatchAuditLog failure(UUID jobId, int batchNumber, int readingCount, String error) {
        return new BatchAuditLog(jobId, batchNumber, readingCount, 0, BatchStatus.FAILED, error);
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public int getBatchNumber() { return batchNumber; }
    public int getReadingCount() { return readingCount; }
    public int getReadingsPersisted() { return readingsPersisted; }
    public BatchStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public enum BatchStatus {
        SUCCESS, FAILED
    }
}
