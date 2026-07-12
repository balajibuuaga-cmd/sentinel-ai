package com.sentinelai.repository;

import com.sentinelai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndTenantId(Long id, String tenantId);

    boolean existsByEmail(String email);

    Optional<User> findByResetTokenHash(String resetTokenHash);

    List<User> findByTenantIdOrderByCreatedAtAsc(String tenantId);
}
