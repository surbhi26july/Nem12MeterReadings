package com.nem12.model.dto;

import com.nem12.model.entity.MeterReading;

import java.util.List;

public record NumberedBatch(int batchNumber, List<MeterReading> readings) {
}
