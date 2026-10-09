package com.assessment.country_info_service.exception;

/**
 * The SOAP provider does not know the requested country.
 */
public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(String message) {
        super(message);
    }
}
