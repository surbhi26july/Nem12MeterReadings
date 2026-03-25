package com.nem12.service.parser;

import com.nem12.model.dto.IntervalBlock;
import com.nem12.model.dto.Nem12Header;
import com.nem12.model.entity.MeterReading;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a parsed 300-record (date + consumption values) and expands it into
 * individual timestamped meter readings.
 * <p>
 * For a 30-minute interval, one 300-record with 48 values becomes 48 readings,
 * each with a timestamp calculated as: date + (index * interval_minutes).
 */
@Component
public class IntervalExpander {

    /**
     * Expands an interval block into individual meter readings.
     *
     * @param header the parent 200-record context (NMI + interval length)
     * @param block  the parsed 300-record
     * @return one MeterReading per interval value
     */
    public List<MeterReading> expand(Nem12Header header, IntervalBlock block) {
        List<BigDecimal> values = block.consumptionValues();
        List<MeterReading> readings = new ArrayList<>(values.size());

        LocalDateTime baseTimestamp = block.intervalDate().atStartOfDay();

        for (int i = 0; i < values.size(); i++) {
            LocalDateTime timestamp = baseTimestamp.plusMinutes((long) i * header.intervalLengthMinutes());
            readings.add(new MeterReading(header.nmi(), timestamp, values.get(i)));
        }

        return readings;
    }
}
