package com.nem12.controller;

import com.nem12.exception.JobNotCompleteException;
import com.nem12.exception.JobNotFoundException;
import com.nem12.model.dto.JobStatusResponse;
import com.nem12.model.enums.JobStatus;
import com.nem12.service.JobTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobTrackingService jobTrackingService;

    @TempDir
    Path tempDir;

    private JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(jobTrackingService);
    }

    @Test
    void shouldListAllJobs() {
        UUID jobId = UUID.randomUUID();
        JobStatusResponse response = JobStatusResponse.from(
                jobId, "test.csv", JobStatus.COMPLETED, "/output/test.sql",
                5, 250, 0, 0, null, null);
        when(jobTrackingService.getAllJobStatuses()).thenReturn(List.of(response));

        var result = controller.listJobs();

        assertEquals(200, result.getStatusCode().value());
        assertEquals(1, result.getBody().size());
        assertEquals("test.csv", result.getBody().get(0).filename());
    }

    @Test
    void shouldReturnEmptyListWhenNoJobs() {
        when(jobTrackingService.getAllJobStatuses()).thenReturn(Collections.emptyList());

        var result = controller.listJobs();

        assertEquals(200, result.getStatusCode().value());
        assertTrue(result.getBody().isEmpty());
    }

    @Test
    void shouldGetJobStatus() {
        UUID jobId = UUID.randomUUID();
        JobStatusResponse response = JobStatusResponse.from(
                jobId, "test.csv", JobStatus.PROCESSING, null,
                5, 250, 0, 0, null, null);
        when(jobTrackingService.getJobStatus(jobId)).thenReturn(response);

        var result = controller.getJobStatus(jobId);

        assertEquals(200, result.getStatusCode().value());
        assertEquals(250, result.getBody().totalReadingsPersisted());
    }

    @Test
    void shouldDownloadSqlFile() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path sqlFile = Files.createFile(tempDir.resolve("test.sql"));
        Files.writeString(sqlFile, "INSERT INTO ...");

        when(jobTrackingService.getSqlFileForDownload(jobId)).thenReturn(sqlFile);

        ResponseEntity<Resource> result = controller.downloadSqlFile(jobId);

        assertEquals(200, result.getStatusCode().value());
        assertTrue(result.getHeaders().getContentDisposition().toString().contains("inserts.sql"));
    }

    @Test
    void shouldThrowWhenDownloadingFromProcessingJob() {
        UUID jobId = UUID.randomUUID();
        when(jobTrackingService.getSqlFileForDownload(jobId))
                .thenThrow(new JobNotCompleteException(jobId, "PROCESSING"));

        assertThrows(JobNotCompleteException.class, () -> controller.downloadSqlFile(jobId));
    }

    @Test
    void shouldThrowWhenSqlFilePathIsNull() {
        UUID jobId = UUID.randomUUID();
        when(jobTrackingService.getSqlFileForDownload(jobId))
                .thenThrow(new JobNotFoundException(jobId));

        assertThrows(JobNotFoundException.class, () -> controller.downloadSqlFile(jobId));
    }

    @Test
    void shouldThrowWhenSqlFileMissingFromDisk() {
        UUID jobId = UUID.randomUUID();
        when(jobTrackingService.getSqlFileForDownload(jobId))
                .thenThrow(new RuntimeException("SQL file not found on disk."));

        assertThrows(RuntimeException.class, () -> controller.downloadSqlFile(jobId));
    }
}
