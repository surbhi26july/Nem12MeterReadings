package com.nem12.service.validation;

import com.nem12.exception.InvalidFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * Validates an uploaded NEM12 file before we bother creating a job or
 * kicking off async processing. The idea is to catch obviously bad files
 * early so we don't waste time on them.
 */
@Component
public class Nem12FileValidator {

    private static final Logger log = LoggerFactory.getLogger(Nem12FileValidator.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "nem12");
    private static final String NEM12_MARKER = "NEM12";
    private static final String RECORD_TYPE_100 = "100";

    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateExtension(file.getOriginalFilename());
        validateNem12Header(file);
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file is empty");
        }
    }

    private void validateExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new InvalidFileException("File must have a .csv or .nem12 extension");
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException(
                    "Unsupported file extension: ." + extension + ". Expected .csv or .nem12"
            );
        }
    }

    /**
     * Reads just the first line of the file to check for a valid NEM12 header.
     * We don't read the whole file here — that happens later during parsing.
     */
    private void validateNem12Header(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String firstLine = reader.readLine();

            if (firstLine == null || firstLine.isBlank()) {
                throw new InvalidFileException("File appears to be empty — no header line found");
            }

            String[] parts = firstLine.split(",");
            if (parts.length < 2) {
                throw new InvalidFileException("Invalid NEM12 header: expected comma-separated values");
            }

            if (!RECORD_TYPE_100.equals(parts[0].trim())) {
                throw new InvalidFileException(
                        "File does not start with a 100 record. Found: " + parts[0].trim()
                );
            }

            if (!NEM12_MARKER.equals(parts[1].trim())) {
                throw new InvalidFileException(
                        "Not a NEM12 file. Header indicates: " + parts[1].trim()
                );
            }

            log.debug("NEM12 header validation passed for file: {}", file.getOriginalFilename());

        } catch (IOException e) {
            throw new InvalidFileException("Could not read file: " + e.getMessage());
        }
    }
}
