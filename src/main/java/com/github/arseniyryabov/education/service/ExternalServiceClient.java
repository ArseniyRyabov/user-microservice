package com.github.arseniyryabov.education.service;

import com.github.arseniyryabov.education.dto.external.OrderResponse;
import com.github.arseniyryabov.education.dto.external.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalServiceClient {

    private final WebClient productWebClient;
    private final WebClient orderWebClient;

    public Mono<ProductResponse> getProductById(UUID productId) {
        log.info("Получение продукта с ID: {}", productId);

        return productWebClient.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> Mono.error(new RuntimeException("Ошибка при получении продукта")))
                .bodyToMono(ProductResponse.class)
                .doOnError(error -> log.error("Ошибка при получении продукта: {}", error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    public Flux<ProductResponse> getAllProducts() {
        log.info("Получение всех продуктов");

        return productWebClient.get()
                .uri("/api/products")
                .retrieve()
                .bodyToFlux(ProductResponse.class);
    }

    public Flux<OrderResponse> getUserOrders(Long userId) {
        log.info("Получение заказов пользователя с ID: {}", userId);

        return orderWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/orders")
                        .build())
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToFlux(OrderResponse.class);
    }

    public Mono<OrderResponse> getOrderById(UUID orderId, Long userId) {
        log.info("Получение заказа с ID: {} для пользователя {}", orderId, userId);

        return orderWebClient.get()
                .uri("/api/orders/{orderId}", orderId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToMono(OrderResponse.class);
    }
}