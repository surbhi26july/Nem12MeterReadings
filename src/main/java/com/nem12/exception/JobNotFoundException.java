package com.nem12.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID jobId) {
        super("No processing job found with id: " + jobId);
    }
}
