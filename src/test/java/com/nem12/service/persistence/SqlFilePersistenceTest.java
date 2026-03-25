package com.nem12.service.persistence;

import com.nem12.model.entity.MeterReading;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlFilePersistenceTest {

    @TempDir
    Path tempDir;

    private SqlFilePersistence persistence;
    private Path outputFile;

    @BeforeEach
    void setUp() {
        outputFile = tempDir.resolve("test_output.sql");
        persistence = new SqlFilePersistence(outputFile);
        persistence.initialize("Test file | Job: test-123");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void shouldWriteFileHeader() throws Exception {
        persistence.close();
        String content = Files.readString(outputFile);

        assertTrue(content.contains("NEM12 Meter Readings Import"));
        assertTrue(content.contains("Test file | Job: test-123"));
    }

    @Test
    void shouldWriteValidInsertStatements() throws Exception {
        MeterReading reading = new MeterReading(
                "NEM1201009",
                LocalDateTime.of(2005, 3, 1, 6, 0),
                new BigDecimal("0.461")
        );

        persistence.persist(List.of(reading));
        persistence.close();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("INSERT INTO meter_readings"));
        assertTrue(content.contains("'NEM1201009'"));
        assertTrue(content.contains("0.461"));
        assertTrue(content.contains("ON CONFLICT (nmi, timestamp) DO UPDATE SET consumption = EXCLUDED.consumption"));
        assertTrue(content.contains("gen_random_uuid()"));
    }

    @Test
    void shouldWriteAtomicTransactionBlock() throws Exception {
        List<MeterReading> readings = List.of(
                new MeterReading("NMI_A", LocalDateTime.of(2005, 3, 1, 0, 0), new BigDecimal("1.0")),
                new MeterReading("NMI_A", LocalDateTime.of(2005, 3, 1, 0, 30), new BigDecimal("2.0")),
                new MeterReading("NMI_B", LocalDateTime.of(2005, 3, 1, 0, 0), new BigDecimal("3.0"))
        );

        persistence.persist(readings, 1);
        persistence.close();

        String content = Files.readString(outputFile);

        // All readings in a single INSERT within BEGIN/COMMIT
        assertTrue(content.contains("BEGIN;"));
        assertTrue(content.contains("COMMIT;"));
        long insertCount = content.lines()
                .filter(line -> line.startsWith("INSERT INTO"))
                .count();
        assertEquals(1, insertCount);

        // Batch header comment
        assertTrue(content.contains("-- Batch 1 (3 readings)"));

        assertTrue(content.contains("'NMI_A'"));
        assertTrue(content.contains("'NMI_B'"));
    }

    @Test
    void shouldReturnCorrectPersistedCount() {
        List<MeterReading> readings = List.of(
                new MeterReading("NMI", LocalDateTime.of(2005, 1, 1, 0, 0), BigDecimal.ONE),
                new MeterReading("NMI", LocalDateTime.of(2005, 1, 1, 0, 30), BigDecimal.TEN)
        );

        int count = persistence.persist(readings);
        assertEquals(2, count);
    }

    @Test
    void shouldHandleMultipleBatchWrites() throws Exception {
        List<MeterReading> batch1 = List.of(
                new MeterReading("NMI", LocalDateTime.of(2005, 1, 1, 0, 0), BigDecimal.ONE)
        );
        List<MeterReading> batch2 = List.of(
                new MeterReading("NMI", LocalDateTime.of(2005, 1, 1, 0, 30), BigDecimal.TEN)
        );

        persistence.persist(batch1, 1);
        persistence.persist(batch2, 2);
        persistence.close();

        String content = Files.readString(outputFile);
        // Two separate BEGIN/COMMIT blocks
        long beginCount = content.lines().filter(line -> line.equals("BEGIN;")).count();
        long commitCount = content.lines().filter(line -> line.equals("COMMIT;")).count();
        assertEquals(2, beginCount);
        assertEquals(2, commitCount);
    }

    @Test
    void shouldEscapeSingleQuotesInNmi() throws Exception {
        MeterReading reading = new MeterReading(
                "NMI'TEST",
                LocalDateTime.of(2005, 3, 1, 0, 0),
                BigDecimal.ONE
        );

        persistence.persist(List.of(reading));
        persistence.close();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("NMI''TEST"));
    }

    @Test
    void shouldThrowIfNotInitialized() {
        SqlFilePersistence uninitialised = new SqlFilePersistence(tempDir.resolve("nope.sql"));

        assertThrows(IllegalStateException.class,
                () -> uninitialised.persist(List.of(
                        new MeterReading("X", LocalDateTime.now(), BigDecimal.ONE)
                )));
    }
}
