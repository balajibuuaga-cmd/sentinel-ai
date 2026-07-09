package com.sentinelai.repository;

import com.sentinelai.model.PullRequestReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {
    List<PullRequestReview> findTop20ByOrderByCreatedAtDesc();

    Optional<PullRequestReview> findTopByOrderByCreatedAtDesc();

    List<PullRequestReview> findTop20ByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<PullRequestReview> findByIdAndTenantId(Long id, String tenantId);

    Optional<PullRequestReview> findTopByTenantIdOrderByCreatedAtDesc(String tenantId);
}
