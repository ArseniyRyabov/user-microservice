package com.github.arseniyryabov.user.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Тело ответа об ошибке. Возвращается GlobalExceptionHandler'ом для
 * всех 4xx/5xx.
 *
 * ## Почему timestamp — Instant, а не LocalDateTime
 *
 * Ранее здесь был LocalDateTime — легаси, без timezone. Это создавало
 * несогласованность с остальными полями времени в проекте
 * (ProcessedEventEntity.processedAt, UserEntity.lastActivityAt,
 * UserCreatedEvent.createdAt — все Instant).
 *
 * Instant сериализуется Jackson'ом как ISO-8601 с UTC-суффиксом:
 *   "2026-11-16T14:23:45.123Z"
 * вместо
 *   "2026-11-16T14:23:45.123"   (LocalDateTime — без timezone)
 *
 * Преимущества:
 *   - timezone-safe: клиент в любой TZ правильно интерпретирует;
 *   - консистентность с событиями и сущностями;
 *   - сравнение timestamp'ов из разных источников корректно.
 *
 * ВАЖНО для клиентов: если кто-то парсил LocalDateTime-формат
 * (без Z), парсер должен принимать ISO-8601 с Z. Современные
 * библиотеки (Jackson, Gson, kotlinx.serialization) — принимают
 * по умолчанию. Legacy-парсеры могут сломаться — это осознанный
 * breaking change ради корректности.
 *
 * @JsonInclude(NON_NULL) — не светим null-поля в JSON.
 * fields заполняется только для validation-ошибок (400), для
 * остальных — null и не сериализуется.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Instant timestamp;
    private String error;
    private String message;
    private String path;
    private Map<String, String> fields;
}
