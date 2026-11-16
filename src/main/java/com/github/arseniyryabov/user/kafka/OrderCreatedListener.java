package com.github.arseniyryabov.user.kafka;

import com.github.arseniyryabov.user.entity.ProcessedEventEntity;
import com.github.arseniyryabov.user.event.OrderCreatedEvent;
import com.github.arseniyryabov.user.repository.ProcessedEventRepository;
import com.github.arseniyryabov.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumer OrderCreatedEvent из order-created.
 *
 * Поток:
 *   Kafka topic order-created
 *     → onOrderCreated(@Transactional)
 *         → defensive checks (eventId/orderId/userId != null)
 *         → idempotency check (processed_events.existsById)
 *         → usersService.handleOrderCreated (бизнес-эффект)
 *         → processed_events.save (фиксация обработки)
 *     → commit транзакции (= commit offset в Kafka при AckMode=BATCH)
 *
 * Гарантии:
 *   - At-least-once: Kafka может доставить сообщение повторно
 *     (consumer упал до commit offset'а, rebalance, retry).
 *     Идемпотентность через processed_events это компенсирует.
 *   - Транзакционность: бизнес-эффект и запись в processed_events
 *     коммитятся атомарно. Если что-то упадёт — откатятся оба.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private static final String PROCESSED_EVENT_TYPE = "OrderCreatedEvent";

    private final UsersService usersService;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Обрабатывает одно сообщение из order-created.
     *
     * @Transactional — вся обработка в одной транзакции:
     *   - handleOrderCreated обновляет UserEntity (dirty checking);
     *   - save(processed_event) фиксирует факт обработки.
     *   Если commit упадёт — откатятся оба изменения, и сообщение
     *   будет доставлено повторно (at-least-once). Идемпотентность
     *   через existsById гарантирует, что бизнес-эффект не задвоится.
     */
    @KafkaListener(topics = "order-created", groupId = "user-service")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {

        // Защита от malformed-событий. Если order-service пришлёт
        // событие с null-полями, handleOrderCreated кинет NPE на
        // findById(null) → retry 10 раз → DLT. Лучше отбросить сразу
        // и залогировать.
        if (event.eventId() == null || event.orderId() == null || event.userId() == null) {
            log.error("Malformed OrderCreatedEvent, skipping: eventId={}, orderId={}, userId={}",
                    event.eventId(), event.orderId(), event.userId());
            return;
        }

        // Идемпотентность: Kafka гарантирует at-least-once, поэтому
        // одно и то же событие может прийти дважды (retry после падения
        // consumer'а, rebalance, ошибка commit offset'а). Проверяем,
        // обрабатывали ли мы уже этот eventId.
        //
        // processed_events.event_id — PRIMARY KEY, поэтому existsById
        // бьёт в индекс, а не full scan.
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Event {} already processed, skipping", event.eventId());
            return;
        }

        log.info("Received OrderCreated: eventId={}, orderId={}, userId={}",
                event.eventId(), event.orderId(), event.userId());

        // Бизнес-эффект: инкремент totalOrders, обновление lastActivityAt.
        // Если пользователь не найден — UserNotFoundException уйдёт в
        // DefaultErrorHandler → retry → DLT (если настроен).
        usersService.handleOrderCreated(event);

        // Фиксация обработки. В той же транзакции, что и бизнес-эффект:
        // либо оба изменения закоммитятся, либо оба откатятся.
        processedEventRepository.save(new ProcessedEventEntity(
                event.eventId(),
                PROCESSED_EVENT_TYPE,
                Instant.now()
        ));
    }
}
