package com.github.arseniyryabov.user.kafka;

import com.github.arseniyryabov.user.event.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Публикует UserCreatedEvent в Kafka ПОСЛЕ commit транзакции создания
 * пользователя.
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) — событие уходит
 * в Kafka только если транзакция UsersService.create() успешно
 * закоммитилась. Это гарантирует, что клиент не получит 201, если
 * транзакция откатилась, и наоборот — событие не уйдёт в Kafka,
 * если пользователь не сохранился в БД.
 *
 * KNOWN LIMITATION (dual-write problem):
 *   Если Kafka недоступна в момент AFTER_COMMIT — событие теряется
 *   (log.error и всё). Users в БД есть, события user-created нет.
 *   Downstream-сервисы (billing, notifications) не узнают о пользователе.
 *
 *   Решение: outbox pattern (таблица outbox + polling publisher
 *   через @Scheduled). Сознательно НЕ внедрено на этом этапе:
 *   текущий скоуп — стабилизация интеграционных тестов, а outbox
 *   меняет архитектуру и добавляет новую точку отказа между БД и
 *   Kafka, которую тоже надо тестировать. Оценка внедрения: ~3-4 часа.
 *
 *   Триггеры, при которых outbox становится обязательным:
 *     - downstream требует гарантию доставки (деньги, биллинг);
 *     - Kafka-кластер без репликации / плохой SLA;
 *     - появляется второй исходящий event (user-updated, user-deleted).
 *
 * fire-and-forget (whenComplete) — намеренно. HTTP-ответ не должен
 * блокироваться на время отправки в Kafka. Тесты поллят топик
 * (см. UserCreationIntegrationTest#pollUserCreatedEvent).
 */
@Slf4j
@Component
public class UserCreatedKafkaPublisher {

    private static final String TOPIC = "user-created";

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    public UserCreatedKafkaPublisher(KafkaTemplate<String, UserCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Sending UserCreatedEvent to Kafka: eventId={}, userId={}",
                event.eventId(), event.userId());

        kafkaTemplate.send(TOPIC, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    // Партиция важна для отладки распределения событий:
                    // Kafka партиционирует по ключу (userId). Если ключ
                    // null — random partition (плохо для порядка).
                    // Если один userId всегда попадает в одну партицию —
                    // порядок событий этого пользователя гарантирован.
                    if (ex == null) {
                        log.info("UserCreatedEvent sent: eventId={}, partition={}, offset={}",
                                event.eventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        // KNOWN LIMITATION: событие потеряно (dual-write).
                        // См. JavaDoc класса.
                        log.error("Failed to send UserCreatedEvent: eventId={}",
                                event.eventId(), ex);
                    }
                });
    }
}
