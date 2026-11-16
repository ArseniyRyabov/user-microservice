package com.github.arseniyryabov.user.service;

import com.github.arseniyryabov.user.dto.UserCreatingRequest;
import com.github.arseniyryabov.user.dto.UserResponse;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.event.OrderCreatedEvent;
import com.github.arseniyryabov.user.event.UserCreatedEvent;
import com.github.arseniyryabov.user.exception.UserNotFoundException;
import com.github.arseniyryabov.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Бизнес-логика user-service.
 *
 * Две роли:
 *   1. Producer — create() сохраняет UserEntity и публикует UserCreatedEvent
 *      (через ApplicationEventPublisher → @TransactionalEventListener(AFTER_COMMIT)
 *      → UserCreatedKafkaPublisher → Kafka topic user-created).
 *   2. Consumer — handleOrderCreated() вызывается из OrderCreatedListener
 *      внутри @Transactional-метода listener'а. Обновляет агрегаты
 *      пользователя (totalOrders, lastActivityAt).
 */
@Slf4j
@Service
public class UsersService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UsersService(UserRepository userRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Создаёт пользователя и публикует UserCreatedEvent.
     *
     * @Transactional — вся операция атомарна. Если save() упадёт,
     * событие не будет опубликовано (publishEvent откатится вместе
     * с транзакцией).
     *
     * UserCreatedEvent уходит в Kafka через
     * @TransactionalEventListener(phase = AFTER_COMMIT) — ТОЛЬКО после
     * успешного commit.
     *
     * @return id созданного пользователя
     */
    @Transactional
    public Long create(UserCreatingRequest request) {
        UserEntity userEntity = new UserEntity();
        userEntity.setLastName(request.getLastName());
        userEntity.setUserName(request.getUserName());
        userEntity.setSecondName(request.getSecondName());
        UserEntity saved = userRepository.save(userEntity);

        // publishEvent НЕ шлёт в Kafka сразу — Spring складывает событие
        // в очередь. Реальная отправка произойдёт в
        // UserCreatedKafkaPublisher#onUserCreated с @TransactionalEventListener(AFTER_COMMIT)
        // — ТОЛЬКО после успешного commit транзакции.
        eventPublisher.publishEvent(
                UserCreatedEvent.of(saved.getId(), saved.getUserName())
        );

        return saved.getId();
    }

    /**
     * GET /users/{id} — read-only транзакция.
     *
     * @Transactional(readOnly = true) — Hibernate выставляет FlushMode.MANUAL,
     * не делает dirty checking, не пытается flush'ить изменения.
     *
     * @throws UserNotFoundException 404, если пользователя нет
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return toResponse(user);
    }

    /**
     * GET /users?lastName=...&page=...&size=... — постраничный список.
     *
     * Два пути:
     *   - lastName null/blank → findAll(pageable)
     *   - lastName задан → findByLastNameContainingIgnoreCase(lastName, pageable)
     *
     * Page.map() применяет toResponse к каждой entity — Spring Data
     * сам сделает это в одной транзакции.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getByFiltersWithPagination(String lastName, Pageable pageable) {
        Page<UserEntity> users;
        if (lastName == null || lastName.isBlank()) {
            users = userRepository.findAll(pageable);
        } else {
            users = userRepository.findByLastNameContainingIgnoreCase(lastName, pageable);
        }
        return users.map(this::toResponse);
    }

    /**
     * Обработка OrderCreatedEvent от order-service.
     *
     * Вызывается ТОЛЬКО из OrderCreatedListener внутри
     * @Transactional-метода listener'а. Транзакция уже открыта снаружи —
     * здесь дополнительный @Transactional не нужен (propagation REQUIRED
     * присоединится к существующей).
     *
     * Бизнес-эффект:
     *   - инкремент user.totalOrders;
     *   - обновление user.lastActivityAt = event.createdAt().
     *
     * ## Почему event.createdAt(), а не Instant.now()
     *
     * lastActivityAt — это время ПОСЛЕДНЕГО ЗАКАЗА, а не время
     * обработки. При retry (Kafka redelivery через 5 минут после
     * сбоя) Instant.now() дал бы «активность 5 минут назад» —
     * некорректно. event.createdAt() — фактическое время заказа,
     * созданного order-service. Это также важно при replay (пере-
     * обработка истории событий) — lastActivityAt должен отражать
     * реальное время заказов, а не время replay.
     *
     * ## Dirty checking
     *
     * save() не нужен: Hibernate увидит изменения в managed entity
     * и сгенерирует UPDATE при commit транзакции listener'а.
     * @Version защищает от конкурентных обновлений (OptimisticLockException
     * → retry сообщения).
     *
     * @throws UserNotFoundException если пользователь не найден.
     *         Уйдёт в DefaultErrorHandler → retry → DLT.
     */
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("User {} created order {} with total {} at {}",
                event.userId(), event.orderId(), event.totalAmount(), event.createdAt());

        UserEntity user = userRepository.findById(event.userId())
                .orElseThrow(() -> new UserNotFoundException(event.userId()));

        user.setTotalOrders(user.getTotalOrders() + 1);
        user.setLastActivityAt(event.createdAt());
        // save() не нужен: dirty checking + @Version сгенерируют UPDATE
        // при commit транзакции listener'а.
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getLastName(),
                user.getSecondName()
        );
    }
}