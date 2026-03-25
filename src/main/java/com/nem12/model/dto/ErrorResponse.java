package com.nem12.model.dto;

import java.time.LocalDateTime;

public record ErrorResponse(String error, String message, String requestId, LocalDateTime timestamp) {

    public static ErrorResponse of(String error, String message, String requestId) {
        return new ErrorResponse(error, message, requestId, LocalDateTime.now());
    }
}
