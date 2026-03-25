package com.nem12.service.persistence;

import com.nem12.model.entity.MeterReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;

/**
 * Appends INSERT statements to a .sql file, one transaction block per batch.
 * Not a Spring singleton — each processing job creates its own instance.
 */
public class SqlFilePersistence {

    private static final Logger log = LoggerFactory.getLogger(SqlFilePersistence.class);

    private final Path outputPath;
    private BufferedWriter writer;

    public SqlFilePersistence(Path outputPath) {
        this.outputPath = outputPath;
    }

    public void initialize(String context) {
        try {
            Files.createDirectories(outputPath.getParent());
            writer = Files.newBufferedWriter(outputPath);
            writer.write("-- NEM12 Meter Readings Import\n");
            writer.write("-- " + context + "\n");
            writer.write("-- \n\n");
        } catch (IOException e) {
            throw new com.nem12.exception.PersistenceException(
                    "Could not create SQL output file: " + outputPath, e);
        }
    }

    public int persist(List<MeterReading> readings) {
        return persist(readings, 0);
    }

    public int persist(List<MeterReading> readings, int batchNumber) {
        if (writer == null) {
            throw new IllegalStateException("Not initialized — call initialize() first");
        }
        try {
            if (batchNumber > 0) {
                writer.write(String.format("\n-- Batch %d (%d readings)\n", batchNumber, readings.size()));
            }
            writer.write("BEGIN;\n");
            writer.write("INSERT INTO meter_readings (id, nmi, timestamp, consumption) VALUES\n");

            for (int i = 0; i < readings.size(); i++) {
                MeterReading r = readings.get(i);
                writer.write("  (gen_random_uuid(), '");
                writer.write(escapeSql(r.getNmi()));
                writer.write("', '");
                writer.write(Timestamp.valueOf(r.getTimestamp()).toString());
                writer.write("', ");
                writer.write(r.getConsumption().toPlainString());
                writer.write(i < readings.size() - 1 ? "),\n" : ")\n");
            }

            writer.write("ON CONFLICT (nmi, timestamp) DO UPDATE SET consumption = EXCLUDED.consumption;\n");
            writer.write("COMMIT;\n");
            writer.flush();
            return readings.size();
        } catch (IOException e) {
            log.error("SQL file write failed for batch {}: {}", batchNumber, e.getMessage());
            throw new com.nem12.exception.PersistenceException("SQL file write failed", e);
        }
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
                log.info("SQL file written: {}", outputPath);
            } catch (IOException e) {
                log.error("Error closing SQL file: {}", e.getMessage());
            }
        }
    }

    public Path getOutputPath() {
        return outputPath;
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }
}
