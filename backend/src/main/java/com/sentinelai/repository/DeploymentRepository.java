package com.sentinelai.repository;

import com.sentinelai.model.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByTenantId(String tenantId);

    Optional<Deployment> findByIdAndTenantId(Long id, String tenantId);

    Optional<Deployment> findByDeploymentKeyAndTenantId(String deploymentKey, String tenantId);
}
