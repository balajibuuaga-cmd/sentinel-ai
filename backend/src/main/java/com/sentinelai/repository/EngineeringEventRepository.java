package com.sentinelai.repository;

import com.sentinelai.model.EngineeringEvent;
import com.sentinelai.model.EngineeringEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EngineeringEventRepository extends JpaRepository<EngineeringEvent, Long> {
    long countByServiceName(String serviceName);

    long countByTenantIdAndServiceName(String tenantId, String serviceName);

    List<EngineeringEvent> findTop20ByServiceNameOrderByOccurredAtDesc(String serviceName);

    List<EngineeringEvent> findTop20ByServiceNameAndEventTypeOrderByOccurredAtDesc(
            String serviceName,
            EngineeringEventType eventType
    );
}
