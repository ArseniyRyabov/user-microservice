--liquibase formatted sql


--changeset arseniyryabov:user_id_sequence
CREATE SEQUENCE IF NOT EXISTS user_id_sequence
START WITH 1
INCREMENT BY 1;

--changeset arseniyryabov:create-users
CREATE TABLE users (
    id INT PRIMARY KEY DEFAULT NEXTVAL('user_id_sequence'),
    user_name VARCHAR(250) NOT NULL,
    last_name VARCHAR(250) NOT NULL,
    second_name VARCHAR(250) NOT NULL
);