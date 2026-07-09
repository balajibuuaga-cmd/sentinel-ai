package com.sentinelai.repository;

import com.sentinelai.model.IntegrationConnection;
import com.sentinelai.model.IntegrationProvider;
import com.sentinelai.model.IntegrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, Long> {
    List<IntegrationConnection> findByTenantIdOrderByProviderAsc(String tenantId);

    List<IntegrationConnection> findByStatus(IntegrationStatus status);

    Optional<IntegrationConnection> findByIdAndTenantId(Long id, String tenantId);

    Optional<IntegrationConnection> findByTenantIdAndProvider(String tenantId, IntegrationProvider provider);

    long countByTenantIdAndStatus(String tenantId, IntegrationStatus status);
}
