package com.fileflow.server.service;

public class ProcessingException extends RuntimeException {
    private final int status;
    public ProcessingException(String message) { this(422, message, null); }
    public ProcessingException(String message, Throwable cause) { this(422, message, cause); }
    public ProcessingException(int status, String message) { this(status, message, null); }
    public ProcessingException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    public int status() { return status; }
}
