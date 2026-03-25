package com.nem12.model.dto;

import java.util.UUID;

public record UploadResponse(UUID jobId, String status, String filename) {
}
