package com.nem12.service;

import com.nem12.config.Nem12Properties;
import com.nem12.model.dto.NumberedBatch;
import com.nem12.model.entity.MeterReading;
import com.nem12.service.persistence.SqlFilePersistence;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentBatchProcessor.class);

    private final MeterReadingService meterReadingService;
    private final JobTrackingService jobTrackingService;
    private final UUID jobId;
    private final Nem12Properties properties;
    private final Counter readingsPersistedCounter;
    private final Counter readingsFailedCounter;

    private ExecutorService dbWorkerPool;
    private SqlFilePersistence sqlFilePersistence;
    private Semaphore batchThrottle;
    private final CopyOnWriteArrayList<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
    private final AtomicInteger totalReadingsPersisted = new AtomicInteger(0);
    private volatile boolean hasFailures = false;

    public ConcurrentBatchProcessor(MeterReadingService meterReadingService,
                                       JobTrackingService jobTrackingService,
                                       UUID jobId,
                                       Nem12Properties properties,
                                       Counter readingsPersistedCounter,
                                       Counter readingsFailedCounter) {
        this.meterReadingService = meterReadingService;
        this.jobTrackingService = jobTrackingService;
        this.jobId = jobId;
        this.properties = properties;
        this.readingsPersistedCounter = readingsPersistedCounter;
        this.readingsFailedCounter = readingsFailedCounter;
    }

    public void start(Path sqlFilePath, String fileContext) {
        int workers = properties.getConcurrentBatchWorkers();
        this.dbWorkerPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "db-batch-worker");
            t.setDaemon(true);
            return t;
        });
        this.batchThrottle = new Semaphore(properties.getMaxInFlightBatches());
        this.sqlFilePersistence = new SqlFilePersistence(sqlFilePath);
        this.sqlFilePersistence.initialize(fileContext);
    }

    public void submitBatch(NumberedBatch batch) {
        try {
            batchThrottle.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> processBatch(batch), dbWorkerPool
        ).whenComplete((result, ex) -> {
            batchThrottle.release();
            if (batch.batchNumber() % 10 == 0) {
                jobTrackingService.updateProgress(jobId);
            }
        });

        futures.add(future);
    }

    private void processBatch(NumberedBatch batch) {
        List<MeterReading> readings = batch.readings();
        log.debug("Job {}: batch {} started on {}", jobId, batch.batchNumber(), Thread.currentThread().getName());
        try {
            int persisted = meterReadingService.batchUpsert(readings);
            totalReadingsPersisted.addAndGet(persisted);
            readingsPersistedCounter.increment(persisted);
            writeSqlFile(batch);
            jobTrackingService.recordBatchSuccess(jobId, batch.batchNumber(), readings.size(), persisted);
            log.debug("Job {}: batch {} done ({} readings)", jobId, batch.batchNumber(), persisted);
        } catch (Exception e) {
            log.warn("Job {}: batch {} failed, retrying once: {}", jobId, batch.batchNumber(), e.getMessage());
            try {
                int persisted = meterReadingService.batchUpsert(readings);
                totalReadingsPersisted.addAndGet(persisted);
                readingsPersistedCounter.increment(persisted);
                writeSqlFile(batch);
                jobTrackingService.recordBatchSuccess(jobId, batch.batchNumber(), readings.size(), persisted);
            } catch (Exception retryEx) {
                log.error("Job {}: batch {} failed on retry: {}", jobId, batch.batchNumber(), retryEx.getMessage());
                readingsFailedCounter.increment(readings.size());
                hasFailures = true;
                jobTrackingService.recordBatchFailure(jobId, batch.batchNumber(), readings.size(), retryEx.getMessage());
            }
        }
    }

    private synchronized void writeSqlFile(NumberedBatch batch) {
        try {
            sqlFilePersistence.persist(batch.readings(), batch.batchNumber());
        } catch (Exception e) {
            log.error("Job {}: SQL file write failed for batch {}: {}", jobId, batch.batchNumber(), e.getMessage());
        }
    }

    public int awaitAllAndClose() {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        sqlFilePersistence.close();
        dbWorkerPool.shutdown();
        return totalReadingsPersisted.get();
    }

    public boolean hasFailures() {
        return hasFailures;
    }

    public void abort() {
        if (sqlFilePersistence != null) sqlFilePersistence.close();
        if (dbWorkerPool != null) dbWorkerPool.shutdownNow();
    }
}
