package com.nem12.service;

import com.nem12.config.Nem12Properties;
import com.nem12.model.entity.ProcessingJob;
import com.nem12.model.enums.JobStatus;
import com.nem12.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobCleanupServiceTest {

    @Mock
    private ProcessingJobRepository jobRepository;

    @Mock
    private JobTrackingService jobTrackingService;

    private JobCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        Nem12Properties properties = new Nem12Properties();
        properties.setJobTimeoutMinutes(30);
        cleanupService = new JobCleanupService(jobRepository, jobTrackingService, properties);
    }

    @Test
    void shouldMarkStaleJobsAsFailed() {
        ProcessingJob staleJob = new ProcessingJob("stale.csv");
        staleJob.markProcessing();

        when(jobRepository.findByStatusAndLastUpdatedAtBefore(eq(JobStatus.PROCESSING), any()))
                .thenReturn(List.of(staleJob));

        cleanupService.detectStaleJobs();

        verify(jobTrackingService).markFailed(eq(staleJob.getId()), contains("timed out"));
    }

    @Test
    void shouldDoNothingWhenNoStaleJobs() {
        when(jobRepository.findByStatusAndLastUpdatedAtBefore(eq(JobStatus.PROCESSING), any()))
                .thenReturn(Collections.emptyList());

        cleanupService.detectStaleJobs();

        verify(jobTrackingService, never()).markFailed(any(), anyString());
    }
}
