package com.nem12.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies that the meter_readings table is accessible and that TimescaleDB
 * is installed. This goes beyond the default DataSource health check which
 * only verifies connectivity.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            // Check that we can actually query the readings table
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM meter_readings WHERE 1=0", Integer.class
            );

            // Check if TimescaleDB extension is present
            String tsVersion = getTimescaleVersion();

            Health.Builder builder = Health.up()
                    .withDetail("meter_readings_table", "accessible");

            if (tsVersion != null) {
                builder.withDetail("timescaledb_version", tsVersion);
            } else {
                builder.withDetail("timescaledb", "not installed — running on plain PostgreSQL");
            }

            return builder.build();

        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private String getTimescaleVersion() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'",
                    String.class
            );
        } catch (Exception e) {
            // Extension not installed — that's fine, we work with plain PostgreSQL too
            return null;
        }
    }
}
