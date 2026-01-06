package com.github.arseniyryabov.user.service;

import com.github.arseniyryabov.user.controller.model.UserCreatingRequest;
import com.github.arseniyryabov.user.entity.UserEntity;
import com.github.arseniyryabov.user.exceptions.UserNotFoundException;
import com.github.arseniyryabov.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsersServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UsersService usersService;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    //@BeforeEach public void setUp(){MockitoAnnotations.openMocks(this);}

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void testCreate() {
        UserCreatingRequest request = new UserCreatingRequest();
        request.setLastName("Петров");
        request.setUserName("Петр");
        request.setSecondName("Петрович");

        UserEntity mockUserEntity = new UserEntity();
        mockUserEntity.setId(1L);
        mockUserEntity.setLastName(request.getLastName());
        mockUserEntity.setUserName(request.getUserName());
        mockUserEntity.setSecondName(request.getSecondName());

        when(userRepository.save(Mockito.any(UserEntity.class))).thenReturn(mockUserEntity);

        Long id = usersService.create(request);

        verify(userRepository).save(any(UserEntity.class));

        assertEquals(1L, id);
    }

    @Test
    public void testGetById() {
        Long id = 1L;
        UserEntity mockUser = new UserEntity(id, "lastName", "userName", "secondName");

        when(userRepository.findById(id)).thenReturn(Optional.of(mockUser));

        UserEntity foundUser = usersService.getById(id);

        assertNotNull(foundUser);
        assertEquals(mockUser.getId(), foundUser.getId());
        assertEquals(mockUser.getUserName(), foundUser.getUserName());
        assertEquals(mockUser.getLastName(), foundUser.getLastName());
        assertEquals(mockUser.getSecondName(), foundUser.getSecondName());

        verify(userRepository).findById(id);
    }

    @Test
    public void testGetById_ThrowsException_WhenUserNotFound() {
        Long id = 2L;

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> {
            usersService.getById(id);
        });

        verify(userRepository).findById(id);
    }

}