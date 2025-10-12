package com.vectordb.main.exception;

public class VectorRepositoryException extends RuntimeException {
    
    public VectorRepositoryException(String message) {
        super(message);
    }
    
    public VectorRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
