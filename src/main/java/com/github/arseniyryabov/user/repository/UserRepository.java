package com.github.arseniyryabov.user.repository;

import com.github.arseniyryabov.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Query(value = "SELECT * FROM users WHERE (:lastName IS NULL OR LOWER(last_name) LIKE LOWER(CONCAT('%', :lastName, '%'))) LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<UserEntity> findWithPagination(@Param("lastName") String lastName,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);
}
