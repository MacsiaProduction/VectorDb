package com.vectordb.main.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(VectorRepositoryException.class)
    public ResponseEntity<Map<String, Object>> handleVectorRepositoryException(VectorRepositoryException e) {
        log.error("Vector repository error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now(),
            "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error", "Vector Repository Error",
            "message", e.getMessage(),
            "path", "N/A"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Invalid argument: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now(),
            "status", HttpStatus.BAD_REQUEST.value(),
            "error", "Bad Request",
            "message", e.getMessage(),
            "path", "N/A"
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now(),
            "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error", "Internal Server Error",
            "message", e.getMessage(),
            "path", "N/A"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now(),
            "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error", "Internal Server Error",
            "message", "An unexpected error occurred",
            "path", "N/A"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
