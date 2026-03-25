package com.nem12.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DatabaseHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new DatabaseHealthIndicator(jdbcTemplate);
    }

    @Test
    void shouldReturnUpWithTimescaleVersion() {
        when(jdbcTemplate.queryForObject(contains("meter_readings"), eq(Integer.class)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("pg_extension"), eq(String.class)))
                .thenReturn("2.11.0");

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("accessible", health.getDetails().get("meter_readings_table"));
        assertEquals("2.11.0", health.getDetails().get("timescaledb_version"));
    }

    @Test
    void shouldReturnUpWithoutTimescale() {
        when(jdbcTemplate.queryForObject(contains("meter_readings"), eq(Integer.class)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("pg_extension"), eq(String.class)))
                .thenThrow(new RuntimeException("no such extension"));

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("not installed — running on plain PostgreSQL",
                health.getDetails().get("timescaledb"));
    }

    @Test
    void shouldReturnDownOnDatabaseError() {
        when(jdbcTemplate.queryForObject(contains("meter_readings"), eq(Integer.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Connection refused", health.getDetails().get("error"));
    }
}
