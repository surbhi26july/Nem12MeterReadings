package com.nem12.model.dto;

/**
 * Holds the context from a 200-record that subsequent 300-records need.
 * The NMI identifies the meter, and the interval length tells us how
 * many readings to expect per day (e.g. 30 min = 48 readings).
 */
public record Nem12Header(String nmi, int intervalLengthMinutes) {

    public int expectedIntervalsPerDay() {
        return 1440 / intervalLengthMinutes;
    }
}
