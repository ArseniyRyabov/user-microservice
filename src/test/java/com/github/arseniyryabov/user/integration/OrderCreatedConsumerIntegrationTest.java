package com.github.arseniyryabov.user.integration;

import com.github.arseniyryabov.user.BaseIntegrationTest;
import com.github.arseniyryabov.user.event.OrderCreatedEvent;
import com.github.arseniyryabov.user.repository.ProcessedEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderCreatedConsumerIntegrationTest extends BaseIntegrationTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // ТОЛЬКО БД. Топики НЕ удаляем — они созданы в @BeforeAll базового класса.
        // Удаление топика вызывает rebalance listener'а → metadata «плывёт» → flaky.
        jdbcTemplate.execute("TRUNCATE TABLE processed_events CASCADE");
    }

    @Test
    @Timeout(120)
    void shouldConsumeOrderCreatedEventAndSaveToProcessedEvents() throws Exception {
        UUID eventId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId, UUID.randomUUID(), 9L,
                BigDecimal.valueOf(100.00), LocalDateTime.now()
        );

        // send с большим таймаутом (60 сек) — первый прогон медленный
        kafkaTemplate.send("order-created", event.userId().toString(), event)
                .get(60, TimeUnit.SECONDS);

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId)).isTrue()
        );

        long count = processedEventRepository.findAll().stream()
                .filter(e -> e.getEventId().equals(eventId))
                .count();
        assertThat(count).isEqualTo(1);
    }
}