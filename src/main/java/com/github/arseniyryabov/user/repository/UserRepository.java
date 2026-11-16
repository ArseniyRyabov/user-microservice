package com.github.arseniyryabov.user.repository;

import com.github.arseniyryabov.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Page<UserEntity> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);
}
