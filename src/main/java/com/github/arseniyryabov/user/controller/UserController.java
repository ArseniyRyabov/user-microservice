package com.github.arseniyryabov.user.controller;

import com.github.arseniyryabov.user.dto.UserCreatingRequest;
import com.github.arseniyryabov.user.dto.UserResponse;
import com.github.arseniyryabov.user.service.UsersService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST API для пользователей.
 *
 * @Validated на классе — включает method-level validation. Без неё
 * @Min/@Max на @RequestParam молча игнорируются, и ?size=1000000
 * уйдёт в БД (full scan + OOM).
 */
@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    private final UsersService usersService;

    public UserController(UsersService usersService) {
        this.usersService = usersService;
    }

    /**
     * GET /users/{id} — пользователь по id.
     *
     * @throws com.github.arseniyryabov.user.exception.UserNotFoundException
     *         404, если пользователя нет.
     */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return usersService.getById(id);
    }

    /**
     * POST /users — создаёт пользователя.
     *
     * Возвращает 201 + Location header + тело (userId).
     * Location: /users/{id} — REST best practice: клиент может сразу
     * сделать GET по этому адресу, не парся тело.
     *
     * Событие UserCreatedEvent уходит в Kafka через
     * @TransactionalEventListener(AFTER_COMMIT) — ТОЛЬКО после commit.
     */
    @PostMapping
    public ResponseEntity<Long> create(@Valid @RequestBody UserCreatingRequest request) {
        Long userId = usersService.create(request);
        return ResponseEntity
                .created(URI.create("/users/" + userId))
                .body(userId);
    }

    /**
     * GET /users?lastName=...&page=...&size=... — постраничный список.
     *
     * page и size ограничены: size <= 100, page >= 0. Без этого
     * клиент может запросить ?size=1000000 и уронить БД.
     *
     * lastName опционален; если null/blank — возвращаются все.
     */
    @GetMapping
    public Page<UserResponse> getByFiltersWithPagination(
            @RequestParam(required = false) String lastName,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return usersService.getByFiltersWithPagination(lastName, pageable);
    }
}