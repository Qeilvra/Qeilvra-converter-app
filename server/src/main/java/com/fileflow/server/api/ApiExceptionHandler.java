package com.fileflow.server.api;

import com.fileflow.server.service.ProcessingException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ProcessingException.class)
    ResponseEntity<Map<String, Object>> processing(ProcessingException error) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, error.getMessage());
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, Object>> request(Exception error) {
        return response(HttpStatus.BAD_REQUEST, error instanceof MaxUploadSizeExceededException ? "The upload exceeds the 50 MB limit." : "The required file was not supplied.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ignored) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Processing failed safely. No document was retained.");
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message, "timestamp", Instant.now().toString()));
    }
}
