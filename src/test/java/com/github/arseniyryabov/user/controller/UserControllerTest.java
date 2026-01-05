package com.github.arseniyryabov.user.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.service.UsersService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;

import java.util.Arrays;
import java.util.List;

public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private UsersService usersService;

    @InjectMocks
    private UserController userController;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    //@BeforeEach public void setUp(){MockitoAnnotations.openMocks(this);}

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void testGetById() throws Exception {
        Long userId = 1L;
        UserEntity userEntity = new UserEntity(userId, "Иванов", "Иван", "Иванович");
        when(usersService.getById(userId)).thenReturn(userEntity);

        mockMvc.perform(get("/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Иван"));

        verify(usersService).getById(userId);
    }

    @Test
    public void testCreate() throws Exception {
        UserCreatingRequest userCreatingRequest = new UserCreatingRequest("Иванов", "Иван", "Иванович");
        Long userId = 1L;
        when(usersService.create(userCreatingRequest)).thenReturn(userId);

        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content("{\"userName\":\"Иван\", \"lastName\":\"Иванов\", \"secondName\":\"Иванович\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(userId.toString()));

        verify(usersService).create(userCreatingRequest);
    }

    @Test
    public void testGetByFiltersWithPagination() throws Exception {
        UserEntity user1 = new UserEntity(1L, "Иванов", "Иван", "Иванович");
        UserEntity user2 = new UserEntity(2L, "Петров", "Петр", "Петрович");
        List<UserEntity> users = Arrays.asList(user1, user2);

        when(usersService.getByFiltersWithPagination("Иванов", 10, 0)).thenReturn(users);

        mockMvc.perform(get("/users")
                        .param("lastName", "Иванов")
                        .param("limit", "10")
                        .param("offset", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userName").value("Иван"))
                .andExpect(jsonPath("$[1].userName").value("Петр"));

        verify(usersService).getByFiltersWithPagination("Иванов", 10, 0);
    }
}