package com.nem12.service.parser;

import com.nem12.model.dto.IntervalBlock;
import com.nem12.model.dto.Nem12Header;
import com.nem12.model.entity.MeterReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class IntervalExpanderTest {

    private IntervalExpander expander;

    @BeforeEach
    void setUp() {
        expander = new IntervalExpander();
    }

    @Test
    void shouldExpand48ValuesFor30MinIntervals() {
        Nem12Header header = new Nem12Header("NEM1201009", 30);
        List<BigDecimal> values = IntStream.range(0, 48)
                .mapToObj(i -> new BigDecimal("0.5"))
                .toList();

        IntervalBlock block = new IntervalBlock(LocalDate.of(2005, 3, 1), values);
        List<MeterReading> readings = expander.expand(header, block);

        assertEquals(48, readings.size());
    }

    @Test
    void shouldCalculateCorrectTimestampsFor30MinIntervals() {
        Nem12Header header = new Nem12Header("TEST_NMI", 30);
        List<BigDecimal> values = IntStream.range(0, 48)
                .mapToObj(i -> BigDecimal.valueOf(i))
                .toList();

        IntervalBlock block = new IntervalBlock(LocalDate.of(2005, 3, 1), values);
        List<MeterReading> readings = expander.expand(header, block);

        // First reading should be midnight
        assertEquals(LocalDateTime.of(2005, 3, 1, 0, 0), readings.get(0).getTimestamp());

        // Second reading should be 00:30
        assertEquals(LocalDateTime.of(2005, 3, 1, 0, 30), readings.get(1).getTimestamp());

        // 12th reading (index 12) should be 06:00
        assertEquals(LocalDateTime.of(2005, 3, 1, 6, 0), readings.get(12).getTimestamp());

        // Last reading (index 47) should be 23:30
        assertEquals(LocalDateTime.of(2005, 3, 1, 23, 30), readings.get(47).getTimestamp());
    }

    @Test
    void shouldExpand96ValuesFor15MinIntervals() {
        Nem12Header header = new Nem12Header("TEST_NMI", 15);
        List<BigDecimal> values = IntStream.range(0, 96)
                .mapToObj(i -> new BigDecimal("1.0"))
                .toList();

        IntervalBlock block = new IntervalBlock(LocalDate.of(2024, 1, 15), values);
        List<MeterReading> readings = expander.expand(header, block);

        assertEquals(96, readings.size());

        // First reading: midnight
        assertEquals(LocalDateTime.of(2024, 1, 15, 0, 0), readings.get(0).getTimestamp());

        // Fourth reading (index 3): 00:45
        assertEquals(LocalDateTime.of(2024, 1, 15, 0, 45), readings.get(3).getTimestamp());

        // Last reading (index 95): 23:45
        assertEquals(LocalDateTime.of(2024, 1, 15, 23, 45), readings.get(95).getTimestamp());
    }

    @Test
    void shouldPreserveNmiAcrossAllReadings() {
        Nem12Header header = new Nem12Header("NEM1201009", 30);
        List<BigDecimal> values = IntStream.range(0, 48)
                .mapToObj(i -> BigDecimal.ONE)
                .toList();

        IntervalBlock block = new IntervalBlock(LocalDate.of(2005, 3, 1), values);
        List<MeterReading> readings = expander.expand(header, block);

        readings.forEach(r -> assertEquals("NEM1201009", r.getNmi()));
    }

    @Test
    void shouldPreserveConsumptionValues() {
        Nem12Header header = new Nem12Header("TEST", 30);
        List<BigDecimal> values = List.of(
                new BigDecimal("0.000"), new BigDecimal("1.234"), new BigDecimal("5.678")
        );

        // Normally there'd be 48 values, but the expander doesn't enforce that —
        // it just works with whatever it receives. Validation happens in the parser.
        IntervalBlock block = new IntervalBlock(LocalDate.of(2005, 3, 1), values);
        List<MeterReading> readings = expander.expand(header, block);

        assertEquals(new BigDecimal("0.000"), readings.get(0).getConsumption());
        assertEquals(new BigDecimal("1.234"), readings.get(1).getConsumption());
        assertEquals(new BigDecimal("5.678"), readings.get(2).getConsumption());
    }

    @Test
    void shouldHandleLeapYearDate() {
        Nem12Header header = new Nem12Header("LEAP", 30);
        List<BigDecimal> values = IntStream.range(0, 48)
                .mapToObj(i -> BigDecimal.ONE)
                .toList();

        IntervalBlock block = new IntervalBlock(LocalDate.of(2024, 2, 29), values);
        List<MeterReading> readings = expander.expand(header, block);

        assertEquals(48, readings.size());
        assertEquals(LocalDate.of(2024, 2, 29), readings.get(0).getTimestamp().toLocalDate());
    }
}
