package com.assessment.country_info_service.exception;

import com.assessment.country_info_service.dto.ApiError;
import com.assessment.country_info_service.filter.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CountryNotFoundException.class)
    ResponseEntity<ApiError> countryNotFound(CountryNotFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No country matching that name was found", req, null);
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception e, HttpServletRequest req) {
        String msg = e instanceof ResourceNotFoundException ? e.getMessage() : "Resource not found";
        return build(HttpStatus.NOT_FOUND, msg, req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParams(
            HandlerMethodValidationException e, HttpServletRequest req) {

        List<String> details = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException e, HttpServletRequest req) {
        List<String> details = e.getConstraintViolations().stream()
                .map(v -> v.getMessage()).toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> badRequest(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Malformed request: check the JSON body and parameter types", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this endpoint", req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type must be application/json", req, null);
    }

    @ExceptionHandler(ExternalServiceException.class)
    ResponseEntity<ApiError> providerDown(ExternalServiceException e, HttpServletRequest req) {
        log.error("External provider failure: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "The country information provider is currently unavailable. Please retry shortly.",
                req, null, "30");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", req, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception path={}", req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Quote the correlationId when reporting it.",
                req, null);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, HttpServletRequest req, List<String> details) {
        return build(status, message, req, details, null);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, HttpServletRequest req,
            List<String> details, String retryAfterSeconds) {
        if (status.is4xxClientError()) {
            log.warn("Request rejected status={} path={} message={}",
                    status.value(), req.getRequestURI(), message);
        }
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), MDC.get(RequestContextFilter.MDC_KEY), details);
        ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
        if (retryAfterSeconds != null) {
            b.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
        }
        return b.body(body);
    }
}
