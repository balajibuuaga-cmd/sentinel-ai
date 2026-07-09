package com.sentinelai.repository;

import com.sentinelai.model.ArchitectureServiceNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArchitectureServiceRepository extends JpaRepository<ArchitectureServiceNode, Long> {
    Optional<ArchitectureServiceNode> findByServiceName(String serviceName);

    List<ArchitectureServiceNode> findByTenantId(String tenantId);

    Optional<ArchitectureServiceNode> findByTenantIdAndServiceName(String tenantId, String serviceName);
}
