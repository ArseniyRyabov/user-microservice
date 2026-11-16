package com.github.arseniyryabov.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Web-конфигурация.
 * Настраивает сериализацию исходящих HTTP-ответов Page<T> → PagedModel.
 * Это Spring Data фича, к HTTP-клиентам отношения не имеет.
 *
 *
 * @EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO) —
 * меняет сериализацию Page<T> на стабильный PagedModel.
 *
 * Без этой аннотации Spring Boot сериализует PageImpl «как есть»
 * и пишет WARN: «Serializing PageImpl instances as-is is not supported,
 * meaning that there is no guarantee about the stability of the resulting
 * JSON structure». Это значит, что формат ответа может поменяться
 * между версиями Spring Data, и клиенты сломаются.
 *
 * С VIA_DTO формат фиксирован: {"content": [...], "page": {size, number,
 * totalElements, totalPages}}. Это контракт, на который можно положиться.
 *
 * Применяется ко всем endpoint'ам, возвращающим Page<T>
 * (сейчас — GET /users).
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class PageSerializationConfig {
}
