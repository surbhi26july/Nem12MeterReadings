package com.nem12.service;

import com.nem12.model.dto.UploadResponse;
import com.nem12.model.entity.ProcessingJob;
import com.nem12.service.validation.Nem12FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final Nem12FileValidator fileValidator;
    private final JobTrackingService jobTrackingService;
    private final Nem12FileProcessor fileProcessor;

    public FileUploadService(Nem12FileValidator fileValidator,
                             JobTrackingService jobTrackingService,
                             Nem12FileProcessor fileProcessor) {
        this.fileValidator = fileValidator;
        this.jobTrackingService = jobTrackingService;
        this.fileProcessor = fileProcessor;
    }

    public UploadResponse handleUpload(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.info("Upload received: {} ({} bytes)", filename, file.getSize());

        fileValidator.validate(file);

        Path tempFile = Files.createTempFile("nem12_", "_" + filename);
        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(tempFile)) {
            in.transferTo(out);
        }

        ProcessingJob job = jobTrackingService.createJob(filename);
        log.info("Job {} created for file: {}", job.getId(), filename);

        fileProcessor.processAsync(job.getId(), tempFile, filename);

        return new UploadResponse(job.getId(), job.getStatus().name(), filename);
    }
}
