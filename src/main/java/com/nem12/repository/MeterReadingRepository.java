package com.nem12.repository;

import com.nem12.model.entity.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {
}
