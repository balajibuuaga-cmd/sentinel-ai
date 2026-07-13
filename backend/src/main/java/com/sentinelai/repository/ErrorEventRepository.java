package com.sentinelai.repository;

import com.sentinelai.model.ErrorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, Long> {
    List<ErrorEvent> findTop50ByTenantIdOrderByOccurredAtDesc(String tenantId);

    long countByTenantIdAndOccurredAtAfter(String tenantId, Instant since);
}
