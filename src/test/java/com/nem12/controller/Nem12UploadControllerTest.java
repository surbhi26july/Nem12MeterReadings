package com.nem12.controller;

import com.nem12.model.dto.UploadResponse;
import com.nem12.service.FileUploadService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Nem12UploadControllerTest {

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private MultipartFile multipartFile;

    private Nem12UploadController controller;

    @BeforeEach
    void setUp() {
        controller = new Nem12UploadController(fileUploadService, new SimpleMeterRegistry());
    }

    @Test
    void shouldAcceptUploadAndReturnJobId() throws Exception {
        UUID jobId = UUID.randomUUID();
        UploadResponse expected = new UploadResponse(jobId, "QUEUED", "test.csv");
        when(fileUploadService.handleUpload(multipartFile)).thenReturn(expected);

        ResponseEntity<UploadResponse> response = controller.uploadFile(multipartFile);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("test.csv", response.getBody().filename());
        assertEquals(jobId, response.getBody().jobId());
        verify(fileUploadService).handleUpload(multipartFile);
    }
}
