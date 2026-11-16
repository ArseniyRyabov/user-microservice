--liquibase formatted sql

--changeset arseniyryabov:user_id_sequence
-- Sequence для генерации id пользователей.
--
-- INCREMENT BY 50 — согласовано с @SequenceGenerator(allocationSize = 50)
-- в UserEntity. Hibernate резервирует блок из 50 id в памяти за один
-- поход в sequence. Если INCREMENT BY не равен allocationSize —
-- коллизия: Hibernate думает «я взял блок 1-50», а sequence возвращает
-- только 2 (шагает по 1), следующий блок пересекается → duplicate key.
--
-- Hibernate сам вызывает nextval() и передаёт id в INSERT явно.
-- Sequence должна существовать ДО первого save() — иначе
-- «relation user_id_sequence does not exist».
--
-- OWNED BY users.id НЕ добавляем: sequence должна жить независимо
-- от таблицы (переживать DROP/CREATE users при пересоздании).
-- Следствие: TRUNCATE users RESTART IDENTITY её НЕ перезапускает —
-- это ОК, потому что изоляция тестов идёт через уникальные бизнес-
-- маркеры (userName), а не через предсказуемый id.
CREATE SEQUENCE IF NOT EXISTS user_id_sequence
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO CYCLE;


--changeset arseniyryabov:create-users
-- Таблица пользователей.
--
-- id — БЕЗ DEFAULT NEXTVAL. Причина:
--
--   1. JPA-путь (userRepository.save):
--      Hibernate сам вызывает nextval('user_id_sequence'), получает N,
--      и передаёт id=N в INSERT явно. DEFAULT на колонке НЕ срабатывает —
--      потому что значение уже передано. То есть DEFAULT в JPA-пути
--      вообще не участвует — это мёртвый код.
--
--   2. Прямой SQL INSERT без id:
--      DEFAULT сработал бы и вызвал nextval, который прыгнул бы на N+50.
--      Sequence оказалась бы на N+100, а Hibernate ещё не выдал блок
--      N+50..N+99. Следующий блок Hibernate пересечётся с «украденным»
--      id → duplicate key.
--
--   3. Без DEFAULT:
--      прямой INSERT ОБЯЗАН указать id явно. Это заставляет программиста
--      подумать «не конфликтует ли мой id с Hibernate-блоком?» — или,
--      лучше, использовать userRepository.save() и не трогать таблицу
--      напрямую.
--
-- Правило проекта: INSERT в users — ТОЛЬКО через JPA-репозиторий.
-- Прямой SQL INSERT используется только в миграциях/фикстурах и
-- ОБЯЗАН указывать id, полученный через nextval('user_id_sequence').
--
-- total_orders / last_activity_at — агрегаты, обновляемые consumer'ом
-- (OrderCreatedListener → UsersService.handleOrderCreated):
--   - total_orders инкрементируется при каждом OrderCreatedEvent;
--   - last_activity_at = время последнего заказа.
-- Нужны для дешёвой аналитики без вызова order-service.
--
-- version — optimistic lock (@Version в UserEntity). NOT NULL DEFAULT 0:
-- Hibernate при первом INSERT ожидает version = 0.
CREATE TABLE users (
                       id                BIGINT       PRIMARY KEY,
                       user_name         VARCHAR(250) NOT NULL,
                       last_name         VARCHAR(250) NOT NULL,
                       second_name       VARCHAR(250) NOT NULL,
                       total_orders      INTEGER      NOT NULL DEFAULT 0,
                       last_activity_at  TIMESTAMPTZ,
                       version           BIGINT       NOT NULL DEFAULT 0
);

-- Индекс на user_name: точечные lookup'ы, будущий unique constraint.
CREATE INDEX idx_users_user_name ON users (user_name);

-- Индекс на last_name для findByLastNameContainingIgnoreCase.
-- ВНИМАНИЕ: LIKE '%value%' НЕ использует B-tree (нужен pg_trgm).
-- Оставлен для будущих «starts with» и точных совпадений;
-- для %contains% — см. отдельную задачу про pg_trgm.
CREATE INDEX idx_users_last_name ON users (last_name);