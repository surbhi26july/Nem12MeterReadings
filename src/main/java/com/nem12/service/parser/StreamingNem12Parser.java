package com.nem12.service.parser;

import com.nem12.model.dto.IntervalBlock;
import com.nem12.model.dto.Nem12Header;
import com.nem12.model.entity.MeterReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads a NEM12 file line by line and emits meter readings in batches.
 * <p>
 * This is a stateful parser — it remembers the current 200-record context (NMI
 * and interval length) as it encounters 300-records. Each 300-record is expanded
 * into individual timestamped readings using the IntervalExpander.
 * <p>
 * The parser never loads the entire file into memory. It reads one line at a time,
 * accumulates readings into a batch, and flushes that batch via the provided callback
 * whenever it reaches the configured batch size.
 */
@Component
public class StreamingNem12Parser implements Nem12Parser {

    private static final Logger log = LoggerFactory.getLogger(StreamingNem12Parser.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final IntervalExpander intervalExpander;

    public StreamingNem12Parser(IntervalExpander intervalExpander) {
        this.intervalExpander = intervalExpander;
    }

    @Override
    public int parse(InputStream input, int batchSize, Consumer<List<MeterReading>> onBatch) {
        List<MeterReading> currentBatch = new ArrayList<>(batchSize);
        Nem12Header currentHeader = null;
        int recordsParsed = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] fields = line.split(",");
                String recordType = fields[0].trim();

                switch (recordType) {
                    case "100":
                        // Header record — we already validated this during upload,
                        // so there's nothing more to do here.
                        log.debug("NEM12 header record at line {}", lineNumber);
                        break;

                    case "200":
                        currentHeader = parse200Record(fields, lineNumber);
                        if (currentHeader != null) {
                            log.debug("NMI context set: {} ({}min intervals)",
                                    currentHeader.nmi(), currentHeader.intervalLengthMinutes());
                        }
                        break;

                    case "300":
                        if (currentHeader == null) {
                            log.warn("Line {}: found 300-record but no 200-record context exists yet. Skipping.", lineNumber);
                            break;
                        }

                        IntervalBlock block = parse300Record(fields, currentHeader, lineNumber);
                        if (block != null) {
                            List<MeterReading> readings = intervalExpander.expand(currentHeader, block);
                            currentBatch.addAll(readings);
                            recordsParsed++;

                            if (currentBatch.size() >= batchSize) {
                                onBatch.accept(new ArrayList<>(currentBatch));
                                currentBatch.clear();
                            }
                        }
                        break;

                    case "400":
                        // Interval event record. Not relevant for this task but it's
                        // part of the NEM12 spec. We just skip it.
                        break;

                    case "500":
                        log.debug("Line {}: data verification record (500). Noted for audit.", lineNumber);
                        break;

                    case "900":
                        log.debug("End of file marker at line {}", lineNumber);
                        break;

                    default:
                        log.warn("Line {}: unrecognised record type '{}'. Skipping.",
                                lineNumber, truncate(recordType, 10));
                        break;
                }
            }
        } catch (Exception e) {
            // Flush what we have before propagating — partial data is better than none
            if (!currentBatch.isEmpty()) {
                onBatch.accept(currentBatch);
                currentBatch = new ArrayList<>();
            }
            throw new com.nem12.exception.Nem12ParseException(
                    "Failed reading file at line " + lineNumber + ": " + e.getMessage(), lineNumber, e);
        }

        // Flush any remaining readings that didn't fill a complete batch
        if (!currentBatch.isEmpty()) {
            onBatch.accept(currentBatch);
        }

        return recordsParsed;
    }

    /**
     * Parses a 200-record to extract the NMI and interval length.
     * Format: 200,NMI,RegisterSuffix,...,IntervalLength,...
     * The NMI is at index 1, interval length at index 8.
     */
    private Nem12Header parse200Record(String[] fields, int lineNumber) {
        if (fields.length < 9) {
            log.error("Line {}: malformed 200-record — expected at least 9 fields, got {}",
                    lineNumber, fields.length);
            return null;
        }

        String nmi = fields[1].trim();
        if (nmi.isEmpty() || nmi.length() > 10) {
            log.error("Line {}: invalid NMI '{}' — must be 1-10 characters", lineNumber, nmi);
            return null;
        }

        try {
            int intervalLength = Integer.parseInt(fields[8].trim());
            if (intervalLength <= 0 || 1440 % intervalLength != 0) {
                log.error("Line {}: invalid interval length {} — must evenly divide 1440",
                        lineNumber, intervalLength);
                return null;
            }
            return new Nem12Header(nmi, intervalLength);
        } catch (NumberFormatException e) {
            log.error("Line {}: could not parse interval length from '{}'", lineNumber, fields[8].trim());
            return null;
        }
    }

    /**
     * Parses a 300-record to extract the interval date and consumption values.
     * Format: 300,Date,Val1,Val2,...,ValN,QualityMethod,...
     * <p>
     * The tricky part is figuring out where the consumption values end and the
     * metadata fields begin. We know how many values to expect from the interval
     * length in the parent 200-record.
     */
    private IntervalBlock parse300Record(String[] fields, Nem12Header header, int lineNumber) {
        int expectedValues = header.expectedIntervalsPerDay();

        // fields[0] = "300", fields[1] = date, fields[2..2+expectedValues-1] = values
        int minimumFields = 2 + expectedValues;
        if (fields.length < minimumFields) {
            log.warn("Line {}: 300-record has {} fields but expected at least {} for {}min intervals. Skipping.",
                    lineNumber, fields.length, minimumFields, header.intervalLengthMinutes());
            return null;
        }

        LocalDate intervalDate;
        try {
            intervalDate = LocalDate.parse(fields[1].trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("Line {}: could not parse date '{}'. Skipping.", lineNumber, fields[1].trim());
            return null;
        }

        List<BigDecimal> values = new ArrayList<>(expectedValues);
        for (int i = 0; i < expectedValues; i++) {
            String raw = fields[2 + i].trim();
            try {
                values.add(new BigDecimal(raw));
            } catch (NumberFormatException e) {
                log.warn("Line {}: non-numeric value '{}' at interval position {}. Using 0.",
                        lineNumber, truncate(raw, 20), i);
                values.add(BigDecimal.ZERO);
            }
        }

        return new IntervalBlock(intervalDate, values);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "null";
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
