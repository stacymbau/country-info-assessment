package com.assessment.country_info_service.exception;

/**
 * Transient transport problem (timeout, connection reset, or HTTP 5xx).
 * The only exception type that is retried.
 */
public class SoapTransportException extends ExternalServiceException {

    public SoapTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
