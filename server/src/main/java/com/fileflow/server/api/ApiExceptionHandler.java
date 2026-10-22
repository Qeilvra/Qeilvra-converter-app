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
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ProcessingException.class)
    ResponseEntity<Map<String, Object>> processing(ProcessingException error) {
        return response(HttpStatus.valueOf(error.status()), error.getMessage());
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<Map<String, Object>> request(Exception error) {
        return response(error instanceof MaxUploadSizeExceededException ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST,
                error instanceof MaxUploadSizeExceededException ? "The upload exceeds the configured size limit." : "The required file was not supplied.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> invalidRequest(Exception error) {
        return response(HttpStatus.BAD_REQUEST, "The request contains invalid data. Check the file, compression level, or reviewed table values.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> unsupported(Exception error) { return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request uses an unsupported content type."); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(Exception error) { return response(HttpStatus.METHOD_NOT_ALLOWED, "This endpoint does not support that request method."); }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> missing(Exception error) { return response(HttpStatus.NOT_FOUND, "The processing endpoint does not exist. Check the service URL and API version."); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ignored) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Processing failed. Try again with a smaller document or contact the service operator.");
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message, "timestamp", Instant.now().toString()));
    }
}
