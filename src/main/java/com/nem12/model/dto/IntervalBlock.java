package com.nem12.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A parsed 300-record: the interval date and consumption values for that day.
 * The number of values depends on the interval length defined in the parent 200-record.
 */
public record IntervalBlock(LocalDate intervalDate, List<BigDecimal> consumptionValues) {
}
