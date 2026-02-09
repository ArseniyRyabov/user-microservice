package com.github.arseniyryabov.user.controller.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatingRequest {
    private String lastName;
    private String userName;
    private String secondName;
}
