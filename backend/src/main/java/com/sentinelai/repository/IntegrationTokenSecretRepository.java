package com.sentinelai.repository;

import com.sentinelai.model.IntegrationTokenSecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationTokenSecretRepository extends JpaRepository<IntegrationTokenSecret, Long> {
    Optional<IntegrationTokenSecret> findBySecretRef(String secretRef);
}
