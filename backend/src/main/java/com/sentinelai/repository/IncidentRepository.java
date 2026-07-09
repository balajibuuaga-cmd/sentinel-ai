package com.sentinelai.repository;

import com.sentinelai.model.Incident;
import com.sentinelai.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<Incident> findByTenantIdAndStatusNotOrderByUpdatedAtDesc(String tenantId, IncidentStatus status);

    Optional<Incident> findByIncidentKeyAndTenantId(String incidentKey, String tenantId);

    Optional<Incident> findByIdAndTenantId(Long id, String tenantId);
}
