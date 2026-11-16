package com.github.arseniyryabov.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Пользователь сервиса.
 *
 * ## Управление id
 *
 * id генерируется ТОЛЬКО Hibernate через @SequenceGenerator +
 * user_id_sequence. На колонке users.id в миграции НЕТ DEFAULT
 * NEXTVAL — это сделано намеренно:
 *
 *   - Hibernate при save() сам вызывает nextval('user_id_sequence')
 *     и передаёт id в INSERT явно. DEFAULT не участвует в JPA-пути
 *     (значение уже передано, fallback не срабатывает).
 *
 *   - DEFAULT NEXTVAL создавал бы «теневой» путь для прямого SQL
 *     INSERT без id: sequence прыгнула бы на N+50, следующий блок
 *     Hibernate пересёкся бы с уже выданным id → duplicate key.
 *
 *   - Без DEFAULT прямой SQL INSERT в users ОБЯЗАН указать id
 *     явно (через nextval или из userRepository.save). Правило
 *     проекта: все INSERT в users — через JPA-репозиторий.
 *
 * ## allocationSize = 50
 *
 * Hibernate резервирует блок из 50 id в памяти за один поход
 * в sequence. Id всё равно выдаются подряд: 1, 2, 3, ..., 50,
 * 51, 52, ..., 100, 101, ... — блок не «прыгает» через 50.
 * Экономит round-trip к БД при bulk-вставках (сейчас не
 * используются, но паттерн стандартный и безопасный).
 *
 * ВАЖНО: INCREMENT BY в sequence ОБЯЗАН равняться allocationSize.
 * Если INCREMENT BY = 1 при allocationSize = 50 — Hibernate будет
 * генерировать id, которых нет в sequence → duplicate key.
 * Согласовано в Liquibase: INCREMENT BY 50 (см. 001-create-*.sql).
 *
 * ## Агрегаты totalOrders / lastActivityAt
 *
 * Обновляются consumer'ом (OrderCreatedListener →
 * UsersService.handleOrderCreated) при получении OrderCreatedEvent.
 * Дешёвая аналитика на стороне user-service без вызова order-service.
 *
 * ## @Version
 *
 * Optimistic lock. Kafka партиционирует order-created по userId,
 * поэтому конкурентные обновления одного пользователя маловероятны,
 * но @Version — дешёвая defense-in-depth. При конфликте —
 * OptimisticLockException, listener ретраит сообщение (см.
 * OrderCreatedListener).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")   // только по id — не по mutable-полям
public class UserEntity {

    /**
     * Управляется Hibernate через user_id_sequence.
     * На колонке в БД НЕТ DEFAULT NEXTVAL — см. JavaDoc класса.
     * оптимизация под bulk-вставки - allocationSize = 50,
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user-id-generator")
    @SequenceGenerator(
            name = "user-id-generator",
            sequenceName = "user_id_sequence",
            allocationSize = 50,
            initialValue = 1
    )
    @Column(name = "id")
    private Long id;

    @Column(name = "last_name", length = 250, nullable = false)
    private String lastName;

    @Column(name = "user_name", length = 250, nullable = false)
    private String userName;

    @Column(name = "second_name", length = 250, nullable = false)
    private String secondName;

    /**
     * Количество заказов пользователя. Инкрементируется в
     * UsersService.handleOrderCreated при получении OrderCreatedEvent.
     * Java-default = 0 — используется Hibernate при INSERT (DB DEFAULT
     * на колонке — fallback для прямых SQL INSERT).
     */
    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders = 0;

    /**
     * Время последней активности (последнего заказа). Обновляется
     * в handleOrderCreated. null — пользователь ещё ничего не заказывал.
     */
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /**
     * Optimistic lock. Инкрементируется Hibernate при каждом UPDATE.
     * Конкурентный listener получит OptimisticLockException и ретраит
     * сообщение — идемпотентность держится за счёт processed_events
     * (см. OrderCreatedListener).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}