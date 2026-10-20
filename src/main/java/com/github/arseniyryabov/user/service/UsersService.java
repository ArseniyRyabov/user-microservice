package com.github.arseniyryabov.user.service;

import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.exception.UserNotFoundException;
import com.github.arseniyryabov.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UsersService {

    private final UserRepository userRepository;

    public UsersService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long create(UserCreatingRequest request) {
        UserEntity userEntity = new UserEntity();
        userEntity.setLastName(request.getLastName());
        userEntity.setUserName(request.getUserName());
        userEntity.setSecondName(request.getSecondName());
        return userRepository.save(userEntity).getId();
    }

    public UserEntity getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public Page<UserEntity> getByFiltersWithPagination(String lastName, Pageable pageable) {
        if (lastName == null || lastName.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByLastNameContainingIgnoreCase(lastName, pageable);
    }
}
