package com.nem12.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meter_readings")
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nmi", nullable = false, length = 10)
    private String nmi;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "consumption", nullable = false)
    private BigDecimal consumption;

    protected MeterReading() {
        // JPA needs this
    }

    public MeterReading(String nmi, LocalDateTime timestamp, BigDecimal consumption) {
        this.nmi = nmi;
        this.timestamp = timestamp;
        this.consumption = consumption;
    }

    public UUID getId() {
        return id;
    }

    public String getNmi() {
        return nmi;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public BigDecimal getConsumption() {
        return consumption;
    }
}
