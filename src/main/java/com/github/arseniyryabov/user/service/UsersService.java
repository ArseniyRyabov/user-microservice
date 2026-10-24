package com.github.arseniyryabov.user.service;

import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;
import com.github.arseniyryabov.user.controller.model.UserResponse;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.event.UserCreatedEvent;
import com.github.arseniyryabov.user.exception.UserNotFoundException;
import com.github.arseniyryabov.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsersService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UsersService(UserRepository userRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long create(UserCreatingRequest request) {
        UserEntity userEntity = new UserEntity();
        userEntity.setLastName(request.getLastName());
        userEntity.setUserName(request.getUserName());
        userEntity.setSecondName(request.getSecondName());
        UserEntity saved = userRepository.save(userEntity);

        // publishEvent НЕ шлёт в Kafka сразу — Spring складывает событие
        // в очередь. Реальная отправка произойдёт в
        // UserCreatedKafkaPublisher#onUserCreated с @TransactionalEventListener(AFTER_COMMIT)
        // — ТОЛЬКО после успешного commit транзакции.
        eventPublisher.publishEvent(
                UserCreatedEvent.of(saved.getId(), saved.getUserName())
        );

        return saved.getId();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getByFiltersWithPagination(String lastName, Pageable pageable) {
        Page<UserEntity> users;
        if (lastName == null || lastName.isBlank()) {
            users = userRepository.findAll(pageable);
        } else {
            users = userRepository.findByLastNameContainingIgnoreCase(lastName, pageable);
        }
        return users.map(this::toResponse);
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getLastName(),
                user.getSecondName()
        );
    }
}