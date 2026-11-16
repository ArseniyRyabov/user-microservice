package com.github.arseniyryabov.user.exception;

/**
 * Пользователь не найден. Бросается из UsersService.getById и
 * UsersService.handleOrderCreated.
 *
 * HTTP-статус (404) задаётся в GlobalExceptionHandler#handleUserNotFoundException,
 * НЕ через @ResponseStatus — единый источник истины для маппинга
 * exception → HTTP status.
 *
 * Конструктор с произвольным String удалён намеренно: он позволял
 * случайно слить sensitive-информацию в 404 (например, «user with
 * email admin@corp.com not found»). Если нужен кастомный текст —
 * добавляйте специфичный factory method, а не generic-конструктор.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("Пользователь с ID " + userId + " не найден");
    }
}
