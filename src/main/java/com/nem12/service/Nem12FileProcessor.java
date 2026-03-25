package com.nem12.service;

import com.nem12.config.Nem12Properties;
import com.nem12.model.dto.NumberedBatch;
import com.nem12.service.parser.Nem12Parser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class Nem12FileProcessor {

    private static final Logger log = LoggerFactory.getLogger(Nem12FileProcessor.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Nem12Parser parser;
    private final MeterReadingService meterReadingService;
    private final JobTrackingService jobTrackingService;
    private final Nem12Properties properties;

    private final Counter recordsParsedCounter;
    private final Counter readingsPersistedCounter;
    private final Counter readingsFailedCounter;
    private final Timer processingTimer;
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    public Nem12FileProcessor(Nem12Parser parser,
                              MeterReadingService meterReadingService,
                              JobTrackingService jobTrackingService,
                              Nem12Properties properties,
                              MeterRegistry registry) {
        this.parser = parser;
        this.meterReadingService = meterReadingService;
        this.jobTrackingService = jobTrackingService;
        this.properties = properties;

        this.recordsParsedCounter = Counter.builder("nem12.records.parsed")
                .description("Total 300-records parsed").register(registry);
        this.readingsPersistedCounter = Counter.builder("nem12.readings.persisted")
                .description("Total readings written to DB").register(registry);
        this.readingsFailedCounter = Counter.builder("nem12.readings.failed")
                .description("Readings that failed to persist").register(registry);
        this.processingTimer = Timer.builder("nem12.file.processing.duration")
                .description("End-to-end file processing time").register(registry);

        registry.gauge("nem12.active.jobs", activeJobs);
    }

    @Async("nem12TaskExecutor")
    public void processAsync(UUID jobId, Path tempFilePath, String originalFilename) {
        MDC.put("jobId", jobId.toString());
        processingTimer.record(() -> doProcess(jobId, tempFilePath, originalFilename));
        MDC.remove("jobId");
    }

    private void doProcess(UUID jobId, Path tempFilePath, String originalFilename) {
        activeJobs.incrementAndGet();
        log.info("Starting job {} for file: {}", jobId, originalFilename);
        jobTrackingService.markProcessing(jobId);

        String sqlFilename = jobId.toString().substring(0, 8) + "_"
                + LocalDateTime.now().format(FILE_TS) + "_inserts.sql";
        Path sqlFilePath = Path.of(properties.getSqlOutputDirectory(), sqlFilename);
        String fileContext = String.format("Source: %s | Job: %s", originalFilename, jobId);

        ConcurrentBatchProcessor batchProcessor = new ConcurrentBatchProcessor(
                meterReadingService, jobTrackingService, jobId, properties,
                readingsPersistedCounter, readingsFailedCounter);

        try {
            batchProcessor.start(sqlFilePath, fileContext);
        } catch (Exception e) {
            log.error("Job {}: failed to create SQL output file: {}", jobId, e.getMessage());
            batchProcessor.abort();
            jobTrackingService.markFailed(jobId, "Could not create SQL file: " + e.getMessage());
            return;
        }

        AtomicInteger batchCount = new AtomicInteger(0);

        try (InputStream fileStream = new FileInputStream(tempFilePath.toFile())) {

            int recordsParsed = parser.parse(fileStream, properties.getBatchSize(), batch -> {
                int num = batchCount.incrementAndGet();
                batchProcessor.submitBatch(new NumberedBatch(num, batch));
            });

            recordsParsedCounter.increment(recordsParsed);
            int totalPersisted = batchProcessor.awaitAllAndClose();

            jobTrackingService.markCompleted(jobId, sqlFilePath.toString(), batchProcessor.hasFailures());
            log.info("Job {} done: {} records, {} readings persisted", jobId, recordsParsed, totalPersisted);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            batchProcessor.abort();
            jobTrackingService.markFailed(jobId, "Processing failed: " + e.getMessage());
        } finally {
            activeJobs.decrementAndGet();
            cleanupTempFile(tempFilePath);
        }
    }

    private void cleanupTempFile(Path tempFile) {
        try {
            java.nio.file.Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            log.warn("Could not delete temp file {}: {}", tempFile, e.getMessage());
        }
    }
}
