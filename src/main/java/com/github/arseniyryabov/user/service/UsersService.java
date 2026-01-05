package com.github.arseniyryabov.user.service;

import com.github.arseniyryabov.user.exceptions.UserNotFoundException;
import com.github.arseniyryabov.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;

import java.util.List;

@Service
public class UsersService {
    @Autowired
    private UserRepository userRepository;

    public Long create(UserCreatingRequest request) {
        UserEntity userEntity = new UserEntity();
        userEntity.setLastName(request.getLastName());
        userEntity.setUserName(request.getUserName());
        userEntity.setSecondName(request.getSecondName());
        UserEntity savedUser = userRepository.save(userEntity);
        return savedUser.getId();
    }

    public UserEntity getById(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new UserNotFoundException("Пользователь с ID " + id + " не найден"));
    }

    public List<UserEntity> getByFiltersWithPagination(String lastName, int limit, int offset) {
        return userRepository.findWithPagination(lastName, limit, offset);
    }
}
