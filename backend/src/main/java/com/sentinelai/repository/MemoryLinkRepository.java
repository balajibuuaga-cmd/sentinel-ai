package com.sentinelai.repository;

import com.sentinelai.model.MemoryLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryLinkRepository extends JpaRepository<MemoryLink, Long> {
    List<MemoryLink> findTop8ByDeploymentIdOrderByConfidenceDescCreatedAtDesc(Long deploymentId);

    List<MemoryLink> findTop8ByTenantIdAndDeploymentIdOrderByConfidenceDescCreatedAtDesc(String tenantId, Long deploymentId);

    boolean existsByDeploymentIdAndEngineeringEvent_Id(Long deploymentId, Long engineeringEventId);

    boolean existsByTenantIdAndDeploymentIdAndEngineeringEvent_Id(String tenantId, Long deploymentId, Long engineeringEventId);
}
