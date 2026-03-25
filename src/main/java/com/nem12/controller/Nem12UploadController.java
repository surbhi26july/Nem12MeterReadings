package com.nem12.controller;

import com.nem12.model.dto.UploadResponse;
import com.nem12.service.FileUploadService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/nem12")
public class Nem12UploadController {

    private final FileUploadService fileUploadService;
    private final Counter uploadCounter;

    public Nem12UploadController(FileUploadService fileUploadService, MeterRegistry meterRegistry) {
        this.fileUploadService = fileUploadService;
        this.uploadCounter = Counter.builder("nem12.files.uploaded")
                .description("NEM12 files uploaded")
                .register(meterRegistry);
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        UploadResponse response = fileUploadService.handleUpload(file);
        uploadCounter.increment();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
