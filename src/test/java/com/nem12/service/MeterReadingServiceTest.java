package com.nem12.service;

import com.nem12.model.entity.MeterReading;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeterReadingServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private MeterReadingService service;

    @BeforeEach
    void setUp() {
        service = new MeterReadingService(jdbc, new SimpleMeterRegistry());
    }

    @Test
    void shouldUpsertAllReadingsAndReturnCount() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(2);

        List<MeterReading> readings = List.of(
                new MeterReading("NMI001", LocalDateTime.of(2024, 1, 1, 0, 0), new BigDecimal("1.5")),
                new MeterReading("NMI001", LocalDateTime.of(2024, 1, 1, 0, 30), new BigDecimal("2.3"))
        );

        int result = service.batchUpsert(readings);

        assertEquals(2, result);
        // Should produce a single multi-row INSERT
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("(?,?,?),(?,?,?)"));
    }

    @Test
    void shouldBuildMultiRowInsertWithOnConflict() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        List<MeterReading> readings = List.of(
                new MeterReading("NMI_X", LocalDateTime.of(2024, 1, 1, 6, 0), new BigDecimal("3.456"))
        );

        service.batchUpsert(readings);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("INSERT INTO meter_readings"));
        assertTrue(sql.contains("ON CONFLICT (nmi, \"timestamp\") DO UPDATE"));
    }

    @Test
    void shouldPropagateExceptionOnFailure() {
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("Connection refused"));

        List<MeterReading> readings = List.of(
                new MeterReading("NMI001", LocalDateTime.of(2024, 1, 1, 0, 0), new BigDecimal("1.0"))
        );

        assertThrows(RuntimeException.class, () -> service.batchUpsert(readings));
    }
}
