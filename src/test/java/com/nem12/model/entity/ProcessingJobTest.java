package com.nem12.model.entity;

import com.nem12.model.enums.JobStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingJobTest {

    @Test
    void shouldStartInQueuedStatus() {
        ProcessingJob job = new ProcessingJob("test.csv");

        assertEquals(JobStatus.QUEUED, job.getStatus());
        assertEquals("test.csv", job.getFilename());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getLastUpdatedAt());
    }

    @Test
    void shouldTransitionToProcessing() {
        ProcessingJob job = new ProcessingJob("test.csv");
        job.markProcessing();

        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertNotNull(job.getStartedAt());
    }

    @Test
    void shouldTransitionToCompleted() {
        ProcessingJob job = new ProcessingJob("test.csv");
        job.markCompleted();

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void shouldTransitionToPartial() {
        ProcessingJob job = new ProcessingJob("test.csv");
        job.markPartial();

        assertEquals(JobStatus.PARTIAL, job.getStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void shouldTransitionToFailed() {
        ProcessingJob job = new ProcessingJob("test.csv");
        job.markFailed();

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void shouldSetSqlFilePath() {
        ProcessingJob job = new ProcessingJob("test.csv");
        job.setSqlFilePath("/output/test.sql");

        assertEquals("/output/test.sql", job.getSqlFilePath());
    }

    @Test
    void shouldTouchLastUpdated() {
        ProcessingJob job = new ProcessingJob("test.csv");
        var before = job.getLastUpdatedAt();
        job.touchLastUpdated();

        assertNotNull(job.getLastUpdatedAt());
    }
}
