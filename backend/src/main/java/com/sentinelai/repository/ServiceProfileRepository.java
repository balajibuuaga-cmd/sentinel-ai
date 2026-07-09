package com.sentinelai.repository;

import com.sentinelai.model.ServiceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProfileRepository extends JpaRepository<ServiceProfile, Long> {
    Optional<ServiceProfile> findByServiceName(String serviceName);

    Optional<ServiceProfile> findByTenantIdAndServiceName(String tenantId, String serviceName);
}
