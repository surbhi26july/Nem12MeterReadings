package com.nem12.service;

import com.nem12.exception.JobNotFoundException;
import com.nem12.model.dto.JobStatusResponse;
import com.nem12.model.entity.BatchAuditLog;
import com.nem12.model.entity.ProcessingJob;
import com.nem12.model.enums.JobStatus;
import com.nem12.repository.BatchAuditLogRepository;
import com.nem12.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobTrackingServiceTest {

    @Mock
    private ProcessingJobRepository jobRepository;

    @Mock
    private BatchAuditLogRepository auditLogRepository;

    private JobTrackingService service;

    @BeforeEach
    void setUp() {
        service = new JobTrackingService(jobRepository, auditLogRepository);
    }

    @Test
    void shouldCreateJobWithQueuedStatus() {
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProcessingJob job = service.createJob("test.csv");

        assertEquals("test.csv", job.getFilename());
        assertEquals(JobStatus.QUEUED, job.getStatus());
        verify(jobRepository).save(any());
    }

    @Test
    void shouldThrowWhenJobNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class, () -> service.getJob(id));
    }

    @Test
    void shouldTransitionToProcessing() {
        ProcessingJob job = new ProcessingJob("file.csv");
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markProcessing(job.getId());

        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertNotNull(job.getStartedAt());
    }

    @Test
    void shouldTransitionToCompletedWhenNoFailures() {
        ProcessingJob job = new ProcessingJob("file.csv");
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markCompleted(job.getId(), "/output/file.sql", false);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("/output/file.sql", job.getSqlFilePath());
    }

    @Test
    void shouldTransitionToPartialWhenHasFailures() {
        ProcessingJob job = new ProcessingJob("file.csv");
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markCompleted(job.getId(), "/output/file.sql", true);

        assertEquals(JobStatus.PARTIAL, job.getStatus());
    }

    @Test
    void shouldTransitionToFailedAndLogAuditEntry() {
        ProcessingJob job = new ProcessingJob("file.csv");
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(job.getId(), "Something broke");

        assertEquals(JobStatus.FAILED, job.getStatus());
        ArgumentCaptor<BatchAuditLog> captor = ArgumentCaptor.forClass(BatchAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("Something broke", captor.getValue().getErrorMessage());
    }

    @Test
    void shouldRecordBatchSuccess() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordBatchSuccess(UUID.randomUUID(), 1, 100, 100);

        ArgumentCaptor<BatchAuditLog> captor = ArgumentCaptor.forClass(BatchAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(BatchAuditLog.BatchStatus.SUCCESS, captor.getValue().getStatus());
        assertEquals(100, captor.getValue().getReadingsPersisted());
    }

    @Test
    void shouldRecordBatchFailure() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordBatchFailure(UUID.randomUUID(), 5, 100, "DB down");

        ArgumentCaptor<BatchAuditLog> captor = ArgumentCaptor.forClass(BatchAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(BatchAuditLog.BatchStatus.FAILED, captor.getValue().getStatus());
        assertEquals("DB down", captor.getValue().getErrorMessage());
    }

    @Test
    void shouldGetJobStatusWithAggregatedStats() {
        ProcessingJob job = new ProcessingJob("file.csv");
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        when(auditLogRepository.getJobStats(any()))
                .thenReturn(new Object[]{10L, 480L, 20L, 2L});

        JobStatusResponse response = service.getJobStatus(job.getId());

        assertEquals("file.csv", response.filename());
        assertEquals(10, response.totalBatchesProcessed());
        assertEquals(480, response.totalReadingsPersisted());
        assertEquals(20, response.failedReadings());
        assertEquals(2, response.totalErrors());
    }

    @Test
    void shouldGetAllJobStatusesInTwoQueries() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProcessingJob job1 = createJobWithId(id1, "a.csv");
        ProcessingJob job2 = createJobWithId(id2, "b.csv");

        when(jobRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(job1, job2));
        when(auditLogRepository.getJobStatsBulk(any()))
                .thenReturn(List.of(
                        new Object[]{id1, 5L, 200L, 0L, 0L},
                        new Object[]{id2, 3L, 100L, 50L, 1L}
                ));

        List<JobStatusResponse> results = service.getAllJobStatuses();

        assertEquals(2, results.size());
        assertEquals(200, results.get(0).totalReadingsPersisted());
        assertEquals(50, results.get(1).failedReadings());
        verify(jobRepository, times(1)).findAllByOrderByCreatedAtDesc(any(Pageable.class));
        verify(auditLogRepository, times(1)).getJobStatsBulk(any());
    }

    private ProcessingJob createJobWithId(UUID id, String filename) throws Exception {
        ProcessingJob job = new ProcessingJob(filename);
        java.lang.reflect.Field idField = ProcessingJob.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(job, id);
        return job;
    }

    @Test
    void shouldUpdateProgress() {
        ProcessingJob job = new ProcessingJob("file.csv");
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateProgress(job.getId());

        assertNotNull(job.getLastUpdatedAt());
        verify(jobRepository).save(any());
    }
}
