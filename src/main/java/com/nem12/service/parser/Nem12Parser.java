package com.nem12.service.parser;

import com.nem12.model.entity.MeterReading;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses a NEM12 file and emits batches of meter readings via a callback.
 * <p>
 * The callback approach lets us stream through files of any size without
 * accumulating everything in memory. The caller decides what to do with
 * each batch (write to DB, append to file, etc.).
 */
public interface Nem12Parser {

    /**
     * Parse the given input stream and invoke the callback for each batch of readings.
     *
     * @param input     the NEM12 file content
     * @param batchSize how many readings to accumulate before invoking the callback
     * @param onBatch   called with each batch of readings
     * @return total number of 300-records parsed
     */
    int parse(InputStream input, int batchSize, Consumer<List<MeterReading>> onBatch);
}
