package com.nem12.model.dto;

import com.nem12.model.enums.JobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        String filename,
        String status,
        int totalBatchesProcessed,
        int totalReadingsPersisted,
        int failedReadings,
        int totalErrors,
        String downloadUrl,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {

    public static JobStatusResponse from(UUID jobId, String filename, JobStatus status,
                                          String sqlFilePath,
                                          int totalBatches, int totalReadingsPersisted,
                                          int failedReadings, int totalErrors,
                                          LocalDateTime startedAt, LocalDateTime completedAt) {

        boolean hasFile = sqlFilePath != null
                && (status == JobStatus.COMPLETED || status == JobStatus.PARTIAL);

        String downloadUrl = hasFile
                ? "/api/v1/nem12/jobs/" + jobId + "/download"
                : null;

        return new JobStatusResponse(
                jobId,
                filename,
                status.name(),
                totalBatches,
                totalReadingsPersisted,
                failedReadings,
                totalErrors,
                downloadUrl,
                startedAt,
                completedAt
        );
    }
}
