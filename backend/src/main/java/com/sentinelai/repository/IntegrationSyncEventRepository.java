package com.sentinelai.repository;

import com.sentinelai.model.IntegrationSyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationSyncEventRepository extends JpaRepository<IntegrationSyncEvent, Long> {
    List<IntegrationSyncEvent> findTop30ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<IntegrationSyncEvent> findTop10ByTenantIdAndIntegrationConnectionIdOrderByCreatedAtDesc(String tenantId, Long integrationConnectionId);
}
