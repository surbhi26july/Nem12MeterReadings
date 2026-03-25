package com.nem12.service;

import com.nem12.config.Nem12Properties;
import com.nem12.model.entity.MeterReading;
import com.nem12.service.parser.Nem12Parser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Nem12FileProcessorTest {

    @Mock
    private Nem12Parser parser;

    @Mock
    private MeterReadingService meterReadingService;

    @Mock
    private JobTrackingService jobTrackingService;

    @TempDir
    Path tempDir;

    private Nem12FileProcessor processor;

    @BeforeEach
    void setUp() {
        Nem12Properties props = new Nem12Properties();
        props.setSqlOutputDirectory(tempDir.resolve("output").toString());
        props.setBatchSize(100);
        props.setJobTimeoutMinutes(30);
        props.setConcurrentBatchWorkers(2);
        props.setMaxInFlightBatches(4);

        processor = new Nem12FileProcessor(parser, meterReadingService,
                jobTrackingService, props, new SimpleMeterRegistry());
    }

    @Test
    void shouldProcessFileSuccessfully() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path tempFile = Files.createTempFile(tempDir, "test", ".csv");
        Files.writeString(tempFile, "test content");

        List<MeterReading> batch = List.of(
                new MeterReading("NMI001", LocalDateTime.now(), new BigDecimal("1.5"))
        );

        when(parser.parse(any(InputStream.class), eq(100), any()))
                .thenAnswer(inv -> {
                    Consumer<List<MeterReading>> cb = inv.getArgument(2);
                    cb.accept(batch);
                    return 1;
                });
        when(meterReadingService.batchUpsert(anyList())).thenReturn(1);

        processor.processAsync(jobId, tempFile, "test.csv");

        verify(jobTrackingService).markProcessing(jobId);
        verify(jobTrackingService).markCompleted(eq(jobId), anyString(), eq(false));
        verify(meterReadingService).batchUpsert(anyList());
    }

    @Test
    void shouldMarkJobFailedOnParseException() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path tempFile = Files.createTempFile(tempDir, "test", ".csv");
        Files.writeString(tempFile, "bad content");

        when(parser.parse(any(InputStream.class), anyInt(), any()))
                .thenThrow(new RuntimeException("Parse error"));

        processor.processAsync(jobId, tempFile, "test.csv");

        verify(jobTrackingService).markFailed(eq(jobId), contains("Parse error"));
    }

    @Test
    void shouldRetryOnceOnDbFailure() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path tempFile = Files.createTempFile(tempDir, "test", ".csv");
        Files.writeString(tempFile, "content");

        List<MeterReading> batch = List.of(
                new MeterReading("NMI001", LocalDateTime.now(), new BigDecimal("1.0"))
        );

        when(parser.parse(any(InputStream.class), anyInt(), any()))
                .thenAnswer(inv -> {
                    Consumer<List<MeterReading>> cb = inv.getArgument(2);
                    cb.accept(batch);
                    return 1;
                });
        when(meterReadingService.batchUpsert(anyList()))
                .thenThrow(new RuntimeException("DB down"))
                .thenReturn(1);

        processor.processAsync(jobId, tempFile, "test.csv");

        verify(meterReadingService, times(2)).batchUpsert(anyList());
        verify(jobTrackingService).markCompleted(eq(jobId), anyString(), eq(false));
    }

    @Test
    void shouldRecordBatchFailureAfterRetryExhausted() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path tempFile = Files.createTempFile(tempDir, "test", ".csv");
        Files.writeString(tempFile, "content");

        List<MeterReading> batch = List.of(
                new MeterReading("NMI001", LocalDateTime.now(), new BigDecimal("1.0"))
        );

        when(parser.parse(any(InputStream.class), anyInt(), any()))
                .thenAnswer(inv -> {
                    Consumer<List<MeterReading>> cb = inv.getArgument(2);
                    cb.accept(batch);
                    return 1;
                });
        when(meterReadingService.batchUpsert(anyList()))
                .thenThrow(new RuntimeException("DB down"));

        processor.processAsync(jobId, tempFile, "test.csv");

        verify(meterReadingService, times(2)).batchUpsert(anyList());
        verify(jobTrackingService).recordBatchFailure(eq(jobId), eq(1), eq(1), anyString());
    }
}
