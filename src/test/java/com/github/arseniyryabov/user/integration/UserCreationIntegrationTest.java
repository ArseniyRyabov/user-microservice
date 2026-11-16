package com.github.arseniyryabov.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.arseniyryabov.user.BaseIntegrationTest;
import com.github.arseniyryabov.user.dto.UserCreatingRequest;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.event.UserCreatedEvent;
import com.github.arseniyryabov.user.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Полный путь: HTTP POST /users → controller → service → save(users) →
 * publishEvent → AFTER_COMMIT → KafkaTemplate.send → топик user-created.
 *
 * Проверяем:
 *   1. HTTP 201 + userId (Long)
 *   2. В БД users есть строка с этим userId
 *   3. В Kafka user-created появилось сообщение с этим userId
 */
class UserCreationIntegrationTest extends BaseIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldCreateUserAndPublishEventToKafka() throws Exception {
        // 1. HTTP POST /users — контроллер возвращает ResponseEntity<Long>,
        //    тело = число (например, 42). Long.class десериализует чисто.
        UserCreatingRequest request = new UserCreatingRequest();
        request.setUserName("it_user");
        request.setLastName("Ivanov");
        request.setSecondName("Ivanovich");

        ResponseEntity<Long> response =
                restTemplate.postForEntity("/users", request, Long.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long userId = response.getBody();
        assertThat(userId).isNotNull().isPositive();

        // 2. В БД users есть строка с этим userId
        UserEntity saved = userRepository.findById(userId).orElseThrow();
        assertThat(saved.getUserName()).isEqualTo("it_user");

        // 3. В Kafka user-created появилось сообщение с этим userId
        UserCreatedEvent event = pollUserCreatedEvent(userId, Duration.ofSeconds(60));
        assertThat(event).isNotNull();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.userName()).isEqualTo("it_user");
        assertThat(event.eventId()).isNotNull();
    }

    /**
     * Читает сообщения из топика "user-created" и ищет то, у которого
     * userId совпадает с переданным. Уникальный groupId — чтобы читать
     * с earliest, не мешая основному consumer'у user-service.
     */
    private UserCreatedEvent pollUserCreatedEvent(Long userId, Duration timeout) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("user-created"));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    UserCreatedEvent event = objectMapper.readValue(record.value(), UserCreatedEvent.class);
                    if (event.userId().equals(userId)) {
                        return event;
                    }
                }
            }
        }
        return null;
    }
}