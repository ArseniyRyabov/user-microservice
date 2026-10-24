package com.github.arseniyryabov.user.controller;

import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;
import com.github.arseniyryabov.user.controller.model.UserResponse;
import com.github.arseniyryabov.user.service.UsersService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UsersService usersService;

    public UserController(UsersService usersService) {
        this.usersService = usersService;
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return usersService.getById(id);
    }

    @PostMapping
    public ResponseEntity<Long> create(@Valid @RequestBody UserCreatingRequest request) {
        Long userId = usersService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userId);
    }

    @GetMapping
    public Page<UserResponse> getByFiltersWithPagination(
            @RequestParam(required = false) String lastName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return usersService.getByFiltersWithPagination(lastName, pageable);
    }
}