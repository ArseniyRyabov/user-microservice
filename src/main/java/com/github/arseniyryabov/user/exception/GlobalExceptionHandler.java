package com.github.arseniyryabov.user.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Глобальная обработка исключений REST API.
 *
 * Наследуется от ResponseEntityExceptionHandler — это даёт готовые
 * handlers для стандартных Spring MVC исключений (validation, malformed
 * JSON, type mismatch и т.д.) с правильными HTTP-статусами.
 *
 * Специфичные handlers — ниже. Exception.class — последний рубеж,
 * должен срабатывать редко.
 *
 * ## Типы времени
 *
 * ErrorResponse.timestamp — Instant (UTC). Сериализуется как
 * ISO-8601 с суффиксом Z. Консистентно с остальными полями времени
 * в проекте (см. ErrorResponse JavaDoc).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * UserNotFoundException → 404 Not Found.
     * Ожидаемая ситуация: клиент запросил несуществующего пользователя.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFoundException(
            UserNotFoundException ex, WebRequest request) {
        log.warn("User not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    /**
     * IllegalArgumentException → 400 Bad Request.
     *
     * Позволяет различать ожидаемые ошибки валидации (например,
     * некорректный диапазон page/size, невалидный UUID в запросе)
     * от «настоящих» 500. Без этого handler'а любой IAE уходил бы
     * в Exception.class → 500, маскируя баг под «сервер упал».
     *
     * В тестах это позволяет проверять конкретный статус 400, а не
     * только generic 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    /**
     * DataIntegrityViolationException → 409 Conflict.
     *
     * Ловит нарушения unique/foreign key constraint'ов. Например,
     * будущий unique index на user_name/email. Даёт клиенту
     * осмысленный 409 вместо generic 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(
            DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation", ex);
        return buildError(HttpStatus.CONFLICT,
                "Data integrity violation", request, null);
    }

    /**
     * MethodArgumentNotValidException → 400 Bad Request.
     *
     * Ловится для @Valid-ошибок на @RequestBody. Возвращает карту
     * field → message, чтобы клиент знал, какое поле невалидно.
     * (Метод унаследован от ResponseEntityExceptionHandler, но
     * переопределён для форматирования fields в ErrorResponse.)
     */
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

    /**
     * handleExceptionInternal — общий handler для всех исключений,
     * которые обрабатывает ResponseEntityExceptionHandler
     * (HttpMessageNotReadableException, TypeMismatchException и т.д.).
     *
     * Логирование разделено по диапазону статуса:
     *   - 4xx → WARN (клиентская ошибка, шум в логах — норма)
     *   - 5xx → ERROR (серверная ошибка, повод для алерта)
     *
     * Без этого разделения 500 логировался бы как WARN, и регрессия
     * не видна в мониторинге.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError()) {
            log.error("Server error {}: {}", statusCode.value(), ex.getMessage(), ex);
        } else {
            log.warn("Client error {}: {}", statusCode.value(), ex.getMessage());
        }
        return buildError(status, ex.getMessage(), request, null);
    }

    /**
     * Последний рубеж: любое непойманное исключение → 500 Internal Server Error.
     *
     * Специфичные handlers выше перехватывают большинство случаев
     * (404, 400, 409). Сюда попадают только по-настоящему неожиданные
     * ошибки (NPE, AIOOBE, баг в коде).
     *
     * Наружу отдаётся generic-сообщение, стектрейс — только в лог.
     * Никогда не светить stacktrace клиенту.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Internal server error", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "Произошла внутренняя ошибка сервера", request, null);
    }

    /**
     * Единая точка сборки ErrorResponse. Все handlers зовут этот метод
     * — формат ответа консистентен across endpoint'ов.
     *
     * timestamp = Instant.now() (UTC), не LocalDateTime.now().
     * См. ErrorResponse JavaDoc — почему Instant.
     */
    private ResponseEntity<Object> buildError(
            HttpStatus status, String message, WebRequest request, Map<String, String> fields) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .error(status.getReasonPhrase())
                .message(message)
                .path(extractPath(request))
                .fields(fields)
                .build();
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Достаёт path из WebRequest. getDescription(false) возвращает
     * "uri=/users/42"; отрезаем префикс "uri=".
     */
    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}