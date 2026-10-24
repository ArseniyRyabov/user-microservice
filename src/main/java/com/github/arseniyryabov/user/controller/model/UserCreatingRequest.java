package com.github.arseniyryabov.user.controller.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatingRequest {

    @NotBlank(message = "lastName is required")
    @Size(max = 250, message = "lastName must be at most 250 characters")
    private String lastName;

    @NotBlank(message = "userName is required")
    @Size(max = 250, message = "userName must be at most 250 characters")
    private String userName;

    @NotBlank(message = "secondName is required")
    @Size(max = 250, message = "secondName must be at most 250 characters")
    private String secondName;
}
