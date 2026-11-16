package com.github.arseniyryabov.user.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Событие «заказ создан», публикуется order-service в топик
 * order-created. Читается OrderCreatedListener'ом в user-service.
 *
 * ## !!! BREAKING CONTRACT CHANGE: createdAt — Instant (было LocalDateTime)
 *
 * Ранее createdAt был LocalDateTime — без timezone. Это создавало:
 *   - несогласованность с UserCreatedEvent.createdAt (Instant);
 *   - риск таймзонных багов: order-service в TZ +04:00 пишет
 *     "2026-11-16T14:00:00", user-service в TZ +00:00 читает его
 *     как "14:00 UTC" — фактически 10:00 UTC. Разница 4 часа.
 *
 * Причины перехода на Instant:
 *   1. Timezone-safety: Instant — всегда UTC, однозначен.
 *   2. Консистентность: UserCreatedEvent.createdAt уже Instant.
 *   3. Instant.now() в UsersService.handleOrderCreated заменён на
 *      event.createdAt() — используем время СОБЫТИЯ, а не время
 *      ОБРАБОТКИ (корректно при retry/replay).
 *
 * ## Что нужно сделать перед деплоем
 *
 * Это изменение КОНТРАКТА между сервисами. Варианты:
 *
 *   A. Одновременный деплой order-service и user-service:
 *      order-service начинает писать Instant (ISO-8601 с Z),
 *      user-service читает Instant. Окно несовместимости
 *      минимизируется через rolling update.
 *
 *   B. Поэтапная миграция (если order-service не готов):
 *      временно оставить LocalDateTime, добавить поле
 *      createdAtInstant (nullable) — order-service заполняет,
 *      когда готов; после миграции всех producer'ов — удалить
 *      LocalDateTime-поле. Требует дисциплины и версионирования
 *      контракта (см. schema registry, если есть).
 *
 *   C. Если контракт фиксирован и менять нельзя — вернуть
 *      LocalDateTime и оставить этот комментарий как KNOWN ISSUE.
 *      План: унификация в следующей мажорной версии API.
 *
 * Пока договорённости с order-service нет — согласуйте вариант A
 * или B с командой order-service. Это НЕ изменение «в одностороннем
 * порядке».
 *
 * ## Сериализация Jackson
 *
 * Instant → "2026-11-16T14:23:45.123Z" (ISO-8601 с UTC).
 * LocalDateTime → "2026-11-16T14:23:45.123" (без timezone).
 *
 * Если order-service пишет LocalDateTime-формат, а user-service
 * ждёт Instant — Jackson упадёт с InvalidFormatException (нет Z).
 * Это защита от рассинхрона, а не баг.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        Long userId,
        BigDecimal totalAmount,
        Instant createdAt
) {}
