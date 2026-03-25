package com.nem12.service.parser;

import com.nem12.model.entity.MeterReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamingNem12ParserTest {

    private StreamingNem12Parser parser;
    private List<List<MeterReading>> capturedBatches;

    @BeforeEach
    void setUp() {
        parser = new StreamingNem12Parser(new IntervalExpander());
        capturedBatches = new ArrayList<>();
    }

    @Test
    void shouldParseSingleNmiWithOneDay() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                200,NEM1201009,E1E2,1,E1,N1,01009,kWh,30,20050610
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.461,0.810,0.568,1.234,1.353,1.507,1.344,1.773,0.848,1.271,0.895,1.327,1.013,1.793,0.988,0.985,0.876,0.555,0.760,0.938,0.566,0.512,0.970,0.760,0.731,0.615,0.886,0.531,0.774,0.712,0.598,0.670,0.587,0.657,0.345,0.231,A,,,20050310121004,20050310182204
                900
                """;

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        assertEquals(1, records);
        assertEquals(1, capturedBatches.size());
        assertEquals(48, capturedBatches.get(0).size());

        // Verify the NMI is correct
        MeterReading first = capturedBatches.get(0).get(0);
        assertEquals("NEM1201009", first.getNmi());

        // First 12 values are zero (indices 0-11)
        assertEquals(0, BigDecimal.ZERO.compareTo(first.getConsumption()));

        // Index 12 should be 0.461 (first non-zero value)
        MeterReading thirteenth = capturedBatches.get(0).get(12);
        assertEquals(0, new BigDecimal("0.461").compareTo(thirteenth.getConsumption()));
    }

    @Test
    void shouldParseMultipleNmis() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                200,NEM1201009,E1E2,1,E1,N1,01009,kWh,30,20050610
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.461,0.810,0.568,1.234,1.353,1.507,1.344,1.773,0.848,1.271,0.895,1.327,1.013,1.793,0.988,0.985,0.876,0.555,0.760,0.938,0.566,0.512,0.970,0.760,0.731,0.615,0.886,0.531,0.774,0.712,0.598,0.670,0.587,0.657,0.345,0.231,A,,,20050310121004,20050310182204
                500,O,S01009,20050310121004,
                200,NEM1201010,E1E2,2,E2,,01009,kWh,30,20050610
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.154,0.460,0.770,1.003,1.059,1.750,1.423,1.200,0.980,1.111,0.800,1.403,1.145,1.173,1.065,1.187,0.900,0.998,0.768,1.432,0.899,1.211,0.873,0.786,1.504,0.719,0.817,0.780,0.709,0.700,0.565,0.655,0.543,0.786,0.430,0.432,A,,,20050310121004,
                900
                """;

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        assertEquals(2, records);

        List<MeterReading> allReadings = capturedBatches.stream()
                .flatMap(List::stream)
                .toList();
        assertEquals(96, allReadings.size()); // 48 per day, 2 NMIs

        long nmi1Count = allReadings.stream()
                .filter(r -> "NEM1201009".equals(r.getNmi()))
                .count();
        long nmi2Count = allReadings.stream()
                .filter(r -> "NEM1201010".equals(r.getNmi()))
                .count();

        assertEquals(48, nmi1Count);
        assertEquals(48, nmi2Count);
    }

    @Test
    void shouldFlushBatchesAtConfiguredSize() {
        // 48 readings per day, batch size 48 — should get exactly one batch per 300-record
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                200,NEM1201009,E1E2,1,E1,N1,01009,kWh,30,20050610
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.461,0.810,0.568,1.234,1.353,1.507,1.344,1.773,0.848,1.271,0.895,1.327,1.013,1.793,0.988,0.985,0.876,0.555,0.760,0.938,0.566,0.512,0.970,0.760,0.731,0.615,0.886,0.531,0.774,0.712,0.598,0.670,0.587,0.657,0.345,0.231,A,,,20050310121004,20050310182204
                300,20050302,0,0,0,0,0,0,0,0,0,0,0,0,0.235,0.567,0.890,1.123,1.345,1.567,1.543,1.234,0.987,1.123,0.876,1.345,1.145,1.173,1.265,0.987,0.678,0.998,0.768,0.954,0.876,0.845,0.932,0.786,0.999,0.879,0.777,0.578,0.709,0.772,0.625,0.653,0.543,0.599,0.432,0.432,A,,,20050310121004,20050310182204
                900
                """;

        int records = parser.parse(toStream(input), 48, capturedBatches::add);

        assertEquals(2, records);
        assertEquals(2, capturedBatches.size());
        assertEquals(48, capturedBatches.get(0).size());
        assertEquals(48, capturedBatches.get(1).size());
    }

    @Test
    void shouldCalculateCorrectTimestamps() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                200,TEST_NMI,E1E2,1,E1,N1,01009,kWh,30,20050610
                300,20050315,1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0,11.0,12.0,13.0,14.0,15.0,16.0,17.0,18.0,19.0,20.0,21.0,22.0,23.0,24.0,25.0,26.0,27.0,28.0,29.0,30.0,31.0,32.0,33.0,34.0,35.0,36.0,37.0,38.0,39.0,40.0,41.0,42.0,43.0,44.0,45.0,46.0,47.0,48.0,A,,,20050310121004,20050310182204
                900
                """;

        parser.parse(toStream(input), 1000, capturedBatches::add);

        List<MeterReading> readings = capturedBatches.get(0);

        // Midnight
        assertEquals(LocalDateTime.of(2005, 3, 15, 0, 0), readings.get(0).getTimestamp());

        // 06:00 = index 12
        assertEquals(LocalDateTime.of(2005, 3, 15, 6, 0), readings.get(12).getTimestamp());

        // 12:00 = index 24
        assertEquals(LocalDateTime.of(2005, 3, 15, 12, 0), readings.get(24).getTimestamp());

        // 23:30 = index 47
        assertEquals(LocalDateTime.of(2005, 3, 15, 23, 30), readings.get(47).getTimestamp());
    }

    @Test
    void shouldSkip300RecordWithoutPreceding200Record() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.461,0.810,0.568,1.234,1.353,1.507,1.344,1.773,0.848,1.271,0.895,1.327,1.013,1.793,0.988,0.985,0.876,0.555,0.760,0.938,0.566,0.512,0.970,0.760,0.731,0.615,0.886,0.531,0.774,0.712,0.598,0.670,0.587,0.657,0.345,0.231,A,,,20050310121004,20050310182204
                900
                """;

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        assertEquals(0, records);
        assertTrue(capturedBatches.isEmpty());
    }

    @Test
    void shouldHandleMalformed200RecordGracefully() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                200,BAD_RECORD
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.461,0.810,0.568,1.234,1.353,1.507,1.344,1.773,0.848,1.271,0.895,1.327,1.013,1.793,0.988,0.985,0.876,0.555,0.760,0.938,0.566,0.512,0.970,0.760,0.731,0.615,0.886,0.531,0.774,0.712,0.598,0.670,0.587,0.657,0.345,0.231,A,,,20050310121004,20050310182204
                900
                """;

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        // Malformed 200 means no context, so the 300 gets skipped too
        assertEquals(0, records);
    }

    @Test
    void shouldHandleEmptyFile() {
        String input = "";

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        assertEquals(0, records);
        assertTrue(capturedBatches.isEmpty());
    }

    @Test
    void shouldHandleFileWithOnlyHeaderAndFooter() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                900
                """;

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        assertEquals(0, records);
        assertTrue(capturedBatches.isEmpty());
    }

    @Test
    void shouldSkipUnrecognisedRecordTypes() {
        String input = """
                100,NEM12,200506081149,UNITEDDP,NEMMCO
                200,NEM1201009,E1E2,1,E1,N1,01009,kWh,30,20050610
                999,some,unexpected,record
                300,20050301,0,0,0,0,0,0,0,0,0,0,0,0,0.461,0.810,0.568,1.234,1.353,1.507,1.344,1.773,0.848,1.271,0.895,1.327,1.013,1.793,0.988,0.985,0.876,0.555,0.760,0.938,0.566,0.512,0.970,0.760,0.731,0.615,0.886,0.531,0.774,0.712,0.598,0.670,0.587,0.657,0.345,0.231,A,,,20050310121004,20050310182204
                900
                """;

        int records = parser.parse(toStream(input), 1000, capturedBatches::add);

        // The 300-record should still be parsed despite the junk line
        assertEquals(1, records);
        assertEquals(48, capturedBatches.get(0).size());
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
