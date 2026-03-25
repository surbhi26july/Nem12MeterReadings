package com.nem12.model.dto;

import com.nem12.model.enums.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobStatusResponseTest {

    @Test
    void shouldBuildFromCompletedJobWithSqlFile() {
        UUID jobId = UUID.randomUUID();
        JobStatusResponse response = JobStatusResponse.from(
                jobId, "test.csv", JobStatus.COMPLETED, "/output/test.sql",
                10, 480, 0, 0,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now()
        );

        assertEquals("test.csv", response.filename());
        assertEquals("COMPLETED", response.status());
        assertNotNull(response.downloadUrl());
        assertTrue(response.downloadUrl().contains("/download"));
        assertEquals(0, response.totalErrors());
    }

    @Test
    void shouldBuildFromPartialJobWithErrors() {
        UUID jobId = UUID.randomUUID();
        JobStatusResponse response = JobStatusResponse.from(
                jobId, "test.csv", JobStatus.PARTIAL, "/output/test.sql",
                10, 400, 80, 2,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now()
        );

        assertEquals("PARTIAL", response.status());
        assertNotNull(response.downloadUrl());
        assertEquals(80, response.failedReadings());
        assertEquals(2, response.totalErrors());
    }

    @Test
    void shouldBuildFromQueuedJobWithNoDownloadUrl() {
        UUID jobId = UUID.randomUUID();
        JobStatusResponse response = JobStatusResponse.from(
                jobId, "test.csv", JobStatus.QUEUED, null,
                0, 0, 0, 0,
                null, null
        );

        assertEquals("QUEUED", response.status());
        assertNull(response.downloadUrl());
    }

    @Test
    void shouldBuildFromFailedJobWithNoDownloadUrl() {
        UUID jobId = UUID.randomUUID();
        JobStatusResponse response = JobStatusResponse.from(
                jobId, "test.csv", JobStatus.FAILED, "/output/test.sql",
                5, 200, 100, 1,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now()
        );

        assertEquals("FAILED", response.status());
        assertNull(response.downloadUrl());
        assertEquals(1, response.totalErrors());
    }
}
