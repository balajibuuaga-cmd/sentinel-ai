package com.sentinelai.repository;

import com.sentinelai.model.AiUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, Long> {
    List<AiUsageEvent> findByTenantId(String tenantId);

    List<AiUsageEvent> findTop25ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
