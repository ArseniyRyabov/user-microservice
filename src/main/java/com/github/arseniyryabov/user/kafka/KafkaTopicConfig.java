package com.github.arseniyryabov.user.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Декларация Kafka-топиков user-service.
 *
 * Spring Kafka автоматически создаёт NewTopic-бины при старте через
 * KafkaAdmin, если топиков ещё нет. Это production-safe: не полагаемся
 * на auto.create.topics.enable=true у брокера (это анти-паттерн для прода,
 * потому что позволяет любому продюсеру создавать топики с дефолтными
 * настройками).
 *
 * Топики:
 *   - user-created: producer → user-service (создание пользователя).
 *     Читают downstream-сервисы (billing, notifications).
 *   - order-created: producer → order-service, consumer → user-service.
 *     БЕЗ этого бина listener @KafkaListener(topics = "order-created")
 *     не запустится в проде с auto.create.topics.enable=false.
 */
@Configuration
public class KafkaTopicConfig {

    /**
     * Исходящий топик: user-service публикует UserCreatedEvent.
     * 3 партиции — стандарт для горизонтального масштабирования consumer'ов.
     * 1 реплика — локальная разработка / тесты; в проде replicas >= 3.
     */
    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name("user-created")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Входящий топик: order-service публикует OrderCreatedEvent,
     * user-service читает через @KafkaListener.
     *
     * Партиционирование по userId (см. OrderCreatedEvent) гарантирует,
     * что все события одного пользователя попадают в одну партицию
     * и обрабатываются последовательно — это защищает от конкурентных
     * обновлений UserEntity без @Version (но @Version всё равно стоит
     * как defense-in-depth).
     */
    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("order-created")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
