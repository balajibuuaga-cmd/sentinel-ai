package com.sentinelai.repository;

import com.sentinelai.model.WebhookDelivery;
import com.sentinelai.model.WebhookDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    List<WebhookDelivery> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<WebhookDelivery> findByIdAndTenantId(Long id, String tenantId);

    long countByTenantIdAndStatus(String tenantId, WebhookDeliveryStatus status);

    List<WebhookDelivery> findTop50ByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            Collection<WebhookDeliveryStatus> statuses,
            Instant now
    );

    long deleteByStatusInAndExpiresAtLessThanEqual(Collection<WebhookDeliveryStatus> statuses, Instant now);
}
