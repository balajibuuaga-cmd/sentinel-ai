package com.sentinelai.repository;

import com.sentinelai.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop50ByOrderByCreatedAtDesc();

    List<AuditEvent> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
