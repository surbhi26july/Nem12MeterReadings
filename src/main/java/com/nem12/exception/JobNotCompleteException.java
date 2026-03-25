package com.nem12.exception;

import java.util.UUID;

public class JobNotCompleteException extends RuntimeException {

    public JobNotCompleteException(UUID jobId, String currentStatus) {
        super("Job " + jobId + " is not ready for download. Current status: " + currentStatus);
    }
}
