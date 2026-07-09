package com.sentinelai.repository;

import com.sentinelai.model.ArchitectureRisk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchitectureRiskRepository extends JpaRepository<ArchitectureRisk, Long> {
    List<ArchitectureRisk> findTop20ByOrderBySeverityDesc();

    List<ArchitectureRisk> findByTenantId(String tenantId);

    void deleteByTenantId(String tenantId);
}
