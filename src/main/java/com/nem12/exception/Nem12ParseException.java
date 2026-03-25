package com.nem12.exception;

public class Nem12ParseException extends RuntimeException {

    private final int lineNumber;

    public Nem12ParseException(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public Nem12ParseException(String message, int lineNumber, Throwable cause) {
        super(message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
