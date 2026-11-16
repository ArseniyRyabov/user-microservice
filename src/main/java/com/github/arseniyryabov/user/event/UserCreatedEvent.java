package com.github.arseniyryabov.user.event;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
        UUID eventId,
        Long userId,
        String userName,
        Instant createdAt
) {
    public static UserCreatedEvent of(Long userId, String userName) {
        return new UserCreatedEvent(
                UUID.randomUUID(),
                userId,
                userName,
                Instant.now()
        );
    }
}
