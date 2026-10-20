package com.github.arseniyryabov.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "my-generator")
    @SequenceGenerator(name = "my-generator", sequenceName = "user_id_sequence", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "last_name", length = 250)
    private String lastName;

    @Column(name = "user_name", length = 250)
    private String userName;

    @Column(name = "second_name", length = 250)
    private String secondName;
}
