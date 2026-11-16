package com.github.arseniyryabov.user;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton containers pattern (официальный из Testcontainers docs).
 *
 * БЕЗ @Testcontainers и @Container — они перезапускают контейнеры
 * между тестовыми КЛАССАМИ с новыми портами, а Spring context кэширован
 * со старыми портами → Connection refused.
 *
 * static {} — контейнеры стартуют ОДИН РАЗ при загрузке класса в JVM
 * (Gradle test fork = один JVM на прогон) и живут ВЕСЬ прогон.
 * Все тесты (классы) переиспользуют ОДИН Spring context с ОДНИМИ портами.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                    .withEnv("KAFKA_NUM_PARTITIONS", "3");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}