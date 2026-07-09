package com.sentinelai.repository;

import com.sentinelai.model.ArchitectureDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchitectureDependencyRepository extends JpaRepository<ArchitectureDependency, Long> {
    List<ArchitectureDependency> findBySourceService(String sourceService);

    List<ArchitectureDependency> findByTargetService(String targetService);

    boolean existsBySourceServiceAndTargetService(String sourceService, String targetService);

    List<ArchitectureDependency> findByTenantId(String tenantId);

    List<ArchitectureDependency> findByTenantIdAndTargetService(String tenantId, String targetService);

    boolean existsByTenantIdAndSourceServiceAndTargetService(String tenantId, String sourceService, String targetService);
}
