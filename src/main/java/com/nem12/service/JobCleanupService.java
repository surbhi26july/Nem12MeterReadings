package com.nem12.service;

import com.nem12.config.Nem12Properties;
import com.nem12.model.entity.ProcessingJob;
import com.nem12.model.enums.JobStatus;
import com.nem12.repository.ProcessingJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobCleanupService {

    private static final Logger log = LoggerFactory.getLogger(JobCleanupService.class);

    private final ProcessingJobRepository jobRepository;
    private final JobTrackingService jobTrackingService;
    private final Nem12Properties properties;

    public JobCleanupService(ProcessingJobRepository jobRepository,
                             JobTrackingService jobTrackingService,
                             Nem12Properties properties) {
        this.jobRepository = jobRepository;
        this.jobTrackingService = jobTrackingService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${nem12.cleanup-interval-ms}")
    @Transactional
    public void detectStaleJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(properties.getJobTimeoutMinutes());
        List<ProcessingJob> staleJobs = jobRepository.findByStatusAndLastUpdatedAtBefore(
                JobStatus.PROCESSING, cutoff
        );

        for (ProcessingJob job : staleJobs) {
            log.error("Stale job detected: {} (last progress at {}, timeout is {} minutes). Marking as FAILED.",
                    job.getId(), job.getLastUpdatedAt(), properties.getJobTimeoutMinutes());

            jobTrackingService.markFailed(job.getId(),
                    "Processing timed out — no progress detected after "
                    + properties.getJobTimeoutMinutes() + " minutes");
        }

        if (!staleJobs.isEmpty()) {
            log.warn("Marked {} stale job(s) as FAILED", staleJobs.size());
        }
    }
}
