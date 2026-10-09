package com.assessment.country_info_service.exception;

/**
 * The SOAP provider is unavailable or returned something unusable.
 * Maps to HTTP 503.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
