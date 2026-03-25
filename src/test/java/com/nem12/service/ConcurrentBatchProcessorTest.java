package com.nem12.service;

import com.nem12.config.Nem12Properties;
import com.nem12.model.dto.NumberedBatch;
import com.nem12.model.entity.MeterReading;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcurrentBatchProcessorTest {

    @Mock
    private MeterReadingService meterReadingService;

    @Mock
    private JobTrackingService jobTrackingService;

    @TempDir
    Path tempDir;

    private Nem12Properties properties;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new Nem12Properties();
        properties.setBatchSize(100);
        properties.setConcurrentBatchWorkers(2);
        properties.setMaxInFlightBatches(4);
        registry = new SimpleMeterRegistry();
    }

    private List<MeterReading> sampleBatch(String nmi) {
        return List.of(
                new MeterReading(nmi, LocalDateTime.of(2024, 1, 1, 0, 0), new BigDecimal("1.0")),
                new MeterReading(nmi, LocalDateTime.of(2024, 1, 1, 0, 30), new BigDecimal("2.0"))
        );
    }

    @Test
    void shouldProcessMultipleBatchesConcurrently() {
        when(meterReadingService.batchUpsert(anyList())).thenReturn(2);

        Counter persisted = Counter.builder("nem12.readings.persisted").register(registry);
        Counter failed = Counter.builder("nem12.readings.failed").register(registry);

        UUID jobId = UUID.randomUUID();
        ConcurrentBatchProcessor orchestrator = new ConcurrentBatchProcessor(
                meterReadingService, jobTrackingService, jobId, properties,
                persisted, failed);

        Path sqlFile = tempDir.resolve("output.sql");
        orchestrator.start(sqlFile, "Test");

        orchestrator.submitBatch(new NumberedBatch(1, sampleBatch("NMI_A")));
        orchestrator.submitBatch(new NumberedBatch(2, sampleBatch("NMI_B")));
        orchestrator.submitBatch(new NumberedBatch(3, sampleBatch("NMI_C")));

        int total = orchestrator.awaitAllAndClose();

        assertEquals(6, total);
        assertFalse(orchestrator.hasFailures());
        verify(meterReadingService, times(3)).batchUpsert(anyList());
        assertTrue(Files.exists(sqlFile));
    }

    @Test
    void shouldRetryOnDbFailure() {
        when(meterReadingService.batchUpsert(anyList()))
                .thenThrow(new RuntimeException("fail"))
                .thenReturn(2);

        Counter persisted = Counter.builder("nem12.readings.persisted").register(registry);
        Counter failed = Counter.builder("nem12.readings.failed").register(registry);

        UUID jobId = UUID.randomUUID();
        ConcurrentBatchProcessor orchestrator = new ConcurrentBatchProcessor(
                meterReadingService, jobTrackingService, jobId, properties,
                persisted, failed);

        orchestrator.start(tempDir.resolve("output.sql"), "Test");
        orchestrator.submitBatch(new NumberedBatch(1, sampleBatch("NMI_A")));
        int total = orchestrator.awaitAllAndClose();

        assertEquals(2, total);
        assertFalse(orchestrator.hasFailures());
        verify(meterReadingService, times(2)).batchUpsert(anyList());
    }

    @Test
    void shouldRecordBatchErrorAfterRetryExhausted() {
        when(meterReadingService.batchUpsert(anyList()))
                .thenThrow(new RuntimeException("fail"));

        Counter persisted = Counter.builder("nem12.readings.persisted").register(registry);
        Counter failed = Counter.builder("nem12.readings.failed").register(registry);

        UUID jobId = UUID.randomUUID();
        ConcurrentBatchProcessor orchestrator = new ConcurrentBatchProcessor(
                meterReadingService, jobTrackingService, jobId, properties,
                persisted, failed);

        orchestrator.start(tempDir.resolve("output.sql"), "Test");
        orchestrator.submitBatch(new NumberedBatch(1, sampleBatch("NMI_A")));
        int total = orchestrator.awaitAllAndClose();

        assertEquals(0, total);
        assertTrue(orchestrator.hasFailures());
        verify(jobTrackingService).recordBatchFailure(eq(jobId), eq(1), eq(2), anyString());
    }
}
