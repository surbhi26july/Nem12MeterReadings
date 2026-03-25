package com.nem12.service;

import com.nem12.model.entity.MeterReading;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class MeterReadingService {

    private static final Logger log = LoggerFactory.getLogger(MeterReadingService.class);

    private final JdbcTemplate jdbc;
    private final Timer flushTimer;

    public MeterReadingService(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.flushTimer = Timer.builder("nem12.batch.flush.duration")
                .description("Time spent flushing a batch to the database")
                .register(registry);
    }

    @Transactional
    public int batchUpsert(List<MeterReading> readings) {
        return flushTimer.record(() -> {
            // Within a single multi-row INSERT, PostgreSQL doesn't allow ON CONFLICT
            // to touch the same row twice. Dedup by (nmi, timestamp) so the latest
            // correction wins before we hit the database.
            LinkedHashMap<String, MeterReading> seen = new LinkedHashMap<>();
            for (MeterReading r : readings) {
                seen.put(r.getNmi() + "|" + r.getTimestamp(), r);
            }

            List<MeterReading> batch = seen.values().stream()
                    .sorted(Comparator.comparing(MeterReading::getNmi)
                            .thenComparing(MeterReading::getTimestamp))
                    .toList();

            StringBuilder sql = new StringBuilder(batch.size() * 20);
            sql.append("INSERT INTO meter_readings (nmi, \"timestamp\", consumption) VALUES ");

            List<Object> params = new ArrayList<>(batch.size() * 3);
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append("(?,?,?)");
                MeterReading r = batch.get(i);
                params.add(r.getNmi());
                params.add(Timestamp.valueOf(r.getTimestamp()));
                params.add(r.getConsumption());
            }

            sql.append(" ON CONFLICT (nmi, \"timestamp\") DO UPDATE SET consumption = EXCLUDED.consumption");
            jdbc.update(sql.toString(), params.toArray());

            log.debug("Upserted {} readings (deduped from {})", batch.size(), readings.size());
            return batch.size();
        });
    }
}
