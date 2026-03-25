package com.nem12.service;

import com.nem12.exception.JobNotCompleteException;
import com.nem12.exception.JobNotFoundException;
import com.nem12.model.dto.JobStatusResponse;
import com.nem12.model.entity.BatchAuditLog;
import com.nem12.model.entity.ProcessingJob;
import com.nem12.model.enums.JobStatus;
import com.nem12.repository.BatchAuditLogRepository;
import com.nem12.repository.ProcessingJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobTrackingService {

    private final ProcessingJobRepository jobRepository;
    private final BatchAuditLogRepository auditLogRepository;

    public JobTrackingService(ProcessingJobRepository jobRepository,
                              BatchAuditLogRepository auditLogRepository) {
        this.jobRepository = jobRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // -- Job lifecycle --

    @Transactional
    public ProcessingJob createJob(String filename) {
        return jobRepository.save(new ProcessingJob(filename));
    }

    public ProcessingJob getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Transactional
    public void markProcessing(UUID jobId) {
        ProcessingJob job = getJob(jobId);
        job.markProcessing();
        jobRepository.save(job);
    }

    @Transactional
    public void updateProgress(UUID jobId) {
        ProcessingJob job = getJob(jobId);
        job.touchLastUpdated();
        jobRepository.save(job);
    }

    @Transactional
    public void markCompleted(UUID jobId, String sqlFilePath, boolean hasFailures) {
        ProcessingJob job = getJob(jobId);
        job.setSqlFilePath(sqlFilePath);
        if (hasFailures) {
            job.markPartial();
        } else {
            job.markCompleted();
        }
        jobRepository.save(job);
    }

    @Transactional
    public void markFailed(UUID jobId, String reason) {
        ProcessingJob job = getJob(jobId);
        job.markFailed();
        auditLogRepository.save(BatchAuditLog.failure(jobId, 0, 0, reason));
        jobRepository.save(job);
    }

    // -- Batch audit --

    @Transactional
    public void recordBatchSuccess(UUID jobId, int batchNumber, int readingCount, int readingsPersisted) {
        auditLogRepository.save(BatchAuditLog.success(jobId, batchNumber, readingCount, readingsPersisted));
    }

    @Transactional
    public void recordBatchFailure(UUID jobId, int batchNumber, int readingCount, String errorMessage) {
        auditLogRepository.save(BatchAuditLog.failure(jobId, batchNumber, readingCount, errorMessage));
    }

    // -- Status queries --

    /**
     * Single job status — 2 queries total (job + stats).
     */
    public JobStatusResponse getJobStatus(UUID jobId) {
        ProcessingJob job = getJob(jobId);
        int[] stats = extractStats(auditLogRepository.getJobStats(jobId));
        return buildResponse(job, stats);
    }

    /**
     * All job statuses — 2 queries total (jobs + bulk stats), no N+1.
     */
    public List<JobStatusResponse> getAllJobStatuses() {
        List<ProcessingJob> jobs = jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20));
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<UUID> jobIds = jobs.stream().map(ProcessingJob::getId).toList();
        Map<UUID, int[]> statsMap = new HashMap<>();
        for (Object[] row : auditLogRepository.getJobStatsBulk(jobIds)) {
            UUID id = (UUID) row[0];
            statsMap.put(id, new int[]{
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue(),
                    ((Number) row[3]).intValue(),
                    ((Number) row[4]).intValue()
            });
        }

        int[] empty = {0, 0, 0, 0};
        return jobs.stream()
                .map(job -> buildResponse(job, statsMap.getOrDefault(job.getId(), empty)))
                .toList();
    }

    public Page<BatchAuditLog> getErrors(UUID jobId, Pageable pageable) {
        getJob(jobId);
        return auditLogRepository.findByJobIdAndStatusOrderByBatchNumberAsc(
                jobId, BatchAuditLog.BatchStatus.FAILED, pageable);
    }

    /**
     * Returns the SQL file path for download after validating the job is in a terminal state.
     */
    public Path getSqlFileForDownload(UUID jobId) {
        ProcessingJob job = getJob(jobId);

        if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.PROCESSING) {
            throw new JobNotCompleteException(jobId, job.getStatus().name());
        }

        if (job.getSqlFilePath() == null) {
            throw new JobNotFoundException(jobId);
        }

        Path filePath = Path.of(job.getSqlFilePath());
        if (!filePath.toFile().exists()) {
            throw new RuntimeException("SQL file not found on disk. It may have been cleaned up.");
        }

        return filePath;
    }

    // -- Helpers --

    private int[] extractStats(Object[] row) {
        if (row == null || row.length < 4) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{
                ((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(),
                ((Number) row[2]).intValue(),
                ((Number) row[3]).intValue()
        };
    }

    private JobStatusResponse buildResponse(ProcessingJob job, int[] stats) {
        return JobStatusResponse.from(
                job.getId(), job.getFilename(), job.getStatus(), job.getSqlFilePath(),
                stats[0], stats[1], stats[2], stats[3],
                job.getStartedAt(), job.getCompletedAt());
    }
}
