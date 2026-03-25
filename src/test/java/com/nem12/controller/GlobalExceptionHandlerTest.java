package com.nem12.controller;

import com.nem12.exception.*;
import com.nem12.model.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturn400ForInvalidFile() {
        InvalidFileException ex = new InvalidFileException("Bad file format");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidFile(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_FILE", response.getBody().error());
        assertTrue(response.getBody().message().contains("Bad file format"));
    }

    @Test
    void shouldReturn404ForJobNotFound() {
        JobNotFoundException ex = new JobNotFoundException(UUID.randomUUID());
        ResponseEntity<ErrorResponse> response = handler.handleJobNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("JOB_NOT_FOUND", response.getBody().error());
    }

    @Test
    void shouldReturn409ForJobNotComplete() {
        JobNotCompleteException ex = new JobNotCompleteException(UUID.randomUUID(), "PROCESSING");
        ResponseEntity<ErrorResponse> response = handler.handleJobNotComplete(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("JOB_NOT_COMPLETE", response.getBody().error());
        assertTrue(response.getBody().message().contains("PROCESSING"));
    }

    @Test
    void shouldReturn500ForUnexpectedErrors() {
        Exception ex = new RuntimeException("Something unexpected");
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().error());
    }

    @Test
    void shouldAlwaysIncludeTimestamp() {
        InvalidFileException ex = new InvalidFileException("test");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidFile(ex);

        assertNotNull(response.getBody().timestamp());
    }
}
