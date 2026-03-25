package com.nem12.controller;

import com.nem12.model.dto.JobStatusResponse;
import com.nem12.model.dto.PagedResponse;
import com.nem12.model.entity.BatchAuditLog;
import com.nem12.service.JobTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nem12/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobTrackingService jobTrackingService;

    public JobController(JobTrackingService jobTrackingService) {
        this.jobTrackingService = jobTrackingService;
    }

    @GetMapping
    public ResponseEntity<List<JobStatusResponse>> listJobs() {
        return ResponseEntity.ok(jobTrackingService.getAllJobStatuses());
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobTrackingService.getJobStatus(jobId));
    }

    @GetMapping("/{jobId}/errors")
    public ResponseEntity<PagedResponse<BatchAuditLog>> getJobErrors(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PagedResponse.from(jobTrackingService.getErrors(jobId, PageRequest.of(page, size))));
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> downloadSqlFile(@PathVariable UUID jobId) {
        Path filePath = jobTrackingService.getSqlFileForDownload(jobId);

        Resource resource = new FileSystemResource(filePath);
        String downloadFilename = jobId.toString().substring(0, 8) + "_inserts.sql";
        log.info("SQL file download for job {}", jobId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/sql"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFilename + "\"")
                .body(resource);
    }
}
