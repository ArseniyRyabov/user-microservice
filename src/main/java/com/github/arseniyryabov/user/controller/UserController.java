package com.github.arseniyryabov.user.controller;

import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.service.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.github.arseniyryabov.user.controller.model.UserResponse;
import com.github.arseniyryabov.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UsersService usersService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        UserEntity userEntity = usersService.getById(id);
        return new UserResponse(userEntity.getId(), userEntity.getUserName(), userEntity.getLastName(), userEntity.getSecondName());
    }

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody UserCreatingRequest userCreatingRequest) {
        Long userId = usersService.create(userCreatingRequest);
        return new ResponseEntity<>(userId, HttpStatus.CREATED);
    }

    @GetMapping
    public List<UserResponse> getByFiltersWithPagination(
            @RequestParam(required = false, defaultValue = "") String lastName,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<UserEntity> users = usersService.getByFiltersWithPagination(lastName, limit, offset);
        return users.stream()
                .map(user -> new UserResponse(user.getId(), user.getUserName(), user.getLastName(), user.getSecondName()))
                .collect(Collectors.toList());
    }
}
