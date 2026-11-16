--liquibase formatted sql

--changeset arseniyryabov:create-processed-events
-- Таблица идемпотентности consumer'а.
--
-- Каждое успешно обработанное событие (event_id) фиксируется здесь
-- ДО коммита транзакции listener'а. Перед обработкой listener проверяет
-- existsById(eventId) — если true, сообщение скипается.
--
-- Это компенсирует at-least-once в Kafka: одно и то же событие может
-- прийти дважды (retry после падения consumer'а, rebalance, ошибка
-- commit offset'а), но бизнес-эффект применится ровно один раз.
--
-- event_id — PRIMARY KEY: existsById бьёт в индекс, а не full scan.
--
-- processed_at — TIMESTAMPTZ (не TIMESTAMP!): Hibernate 6 маппит
-- java.time.Instant на TIMESTAMP WITH TIME ZONE. Если оставить
-- TIMESTAMP (WITHOUT TIME ZONE), получите ошибку при первом INSERT:
--   «column processed_at is of type timestamp without time zone but
--    expression is of type timestamp with time zone».
CREATE TABLE processed_events (
                                  event_id     UUID         PRIMARY KEY,
                                  event_type   VARCHAR(100) NOT NULL,
                                  processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Индекс на processed_at — для будущей очистки старых записей
-- (scheduled job «DELETE FROM processed_events WHERE processed_at <
-- now() - interval '30 days'»). PK на event_id уже покрывает existsById.
CREATE INDEX idx_processed_events_processed_at
    ON processed_events (processed_at);