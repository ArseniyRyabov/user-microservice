package com.github.arseniyryabov.user.service;

import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;
import com.github.arseniyryabov.user.controller.model.UserResponse;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.exception.UserNotFoundException;
import com.github.arseniyryabov.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsersService {

    private final UserRepository userRepository;

    public UsersService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public Long create(UserCreatingRequest request) {
        UserEntity userEntity = new UserEntity();
        userEntity.setLastName(request.getLastName());
        userEntity.setUserName(request.getUserName());
        userEntity.setSecondName(request.getSecondName());
        return userRepository.save(userEntity).getId();
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