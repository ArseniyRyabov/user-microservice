package com.github.arseniyryabov.user.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFoundException(
            UserNotFoundException ex, WebRequest request) {
        log.warn("User not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fields.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {}", fields);
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", request, fields);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        log.warn("Client error {}: {}", statusCode.value(), ex.getMessage());
        return buildError(status, ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Internal server error", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "Произошла внутренняя ошибка сервера", request, null);
    }

    private ResponseEntity<Object> buildError(
            HttpStatus status, String message, WebRequest request, Map<String, String> fields) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .error(status.getReasonPhrase())
                .message(message)
                .path(extractPath(request))
                .fields(fields)
                .build();
        return ResponseEntity.status(status).body(errorResponse);
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}