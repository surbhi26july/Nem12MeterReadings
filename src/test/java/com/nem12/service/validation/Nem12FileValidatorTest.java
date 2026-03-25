package com.nem12.service.validation;

import com.nem12.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class Nem12FileValidatorTest {

    private Nem12FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new Nem12FileValidator();
    }

    @Test
    void shouldAcceptValidNem12CsvFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "meter_data.csv", "text/csv",
                "100,NEM12,200506081149,UNITEDDP,NEMMCO\n".getBytes()
        );

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void shouldAcceptNem12Extension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.nem12", "text/plain",
                "100,NEM12,200506081149,UNITEDDP,NEMMCO\n".getBytes()
        );

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void shouldRejectNullFile() {
        assertThrows(InvalidFileException.class, () -> validator.validate(null));
    }

    @Test
    void shouldRejectWrongExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.txt", "text/plain",
                "100,NEM12,200506081149,UNITEDDP,NEMMCO\n".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("extension"));
    }

    @Test
    void shouldRejectFileWithoutExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "datafile", "text/plain",
                "100,NEM12,200506081149,UNITEDDP,NEMMCO\n".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("extension"));
    }

    @Test
    void shouldRejectFileNotStartingWith100Record() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.csv", "text/csv",
                "200,NEM1201009,E1E2,1,E1,N1,01009,kWh,30,20050610\n".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("100"));
    }

    @Test
    void shouldRejectNonNem12File() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "other.csv", "text/csv",
                "100,NEM13,200506081149,UNITEDDP,NEMMCO\n".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("NEM12"));
    }

    @Test
    void shouldRejectFileWithOnlyOneField() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.csv", "text/csv",
                "100\n".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("comma-separated"));
    }
}
