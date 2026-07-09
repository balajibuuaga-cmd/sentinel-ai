package com.sentinelai.repository;

import com.sentinelai.model.BackgroundJob;
import com.sentinelai.model.BackgroundJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {
    List<BackgroundJob> findTop10ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(BackgroundJobStatus status, Instant now);

    List<BackgroundJob> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<BackgroundJob> findByIdAndTenantId(Long id, String tenantId);

    long countByTenantIdAndStatus(String tenantId, BackgroundJobStatus status);
}
