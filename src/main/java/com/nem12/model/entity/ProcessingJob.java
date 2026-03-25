package com.nem12.model.entity;

import com.nem12.model.enums.JobStatus;
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
@Table(name = "processing_jobs")
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "sql_file_path", length = 500)
    private String sqlFilePath;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    protected ProcessingJob() {
        // JPA
    }

    public ProcessingJob(String filename) {
        this.filename = filename;
        this.status = JobStatus.QUEUED;
        this.createdAt = LocalDateTime.now();
        this.lastUpdatedAt = this.createdAt;
    }

    // -- State transitions --

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
        this.lastUpdatedAt = this.startedAt;
    }

    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = this.completedAt;
    }

    public void markPartial() {
        this.status = JobStatus.PARTIAL;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = this.completedAt;
    }

    public void markFailed() {
        this.status = JobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.lastUpdatedAt = this.completedAt;
    }

    public void touchLastUpdated() {
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public JobStatus getStatus() { return status; }
    public String getSqlFilePath() { return sqlFilePath; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }

    public void setSqlFilePath(String sqlFilePath) {
        this.sqlFilePath = sqlFilePath;
    }
}
