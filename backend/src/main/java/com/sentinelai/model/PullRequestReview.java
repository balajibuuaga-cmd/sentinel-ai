package com.sentinelai.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pull_request_reviews")
public class PullRequestReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String repository;

    @Column(nullable = false)
    private int prNumber;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String ownerTeam;

    @Column(nullable = false)
    private String ciStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pull_request_changed_files", joinColumns = @JoinColumn(name = "pull_request_review_id"))
    @Column(name = "changed_file")
    @OrderColumn(name = "sort_order")
    private List<String> changedFiles = new ArrayList<>();

    private Long linkedDeploymentId;

    @Column(nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PullRequestRecommendation recommendation;

    @Column(nullable = false, length = 1600)
    private String explanation;

    @Enumerated(EnumType.STRING)
    private PullRequestDecision decision;

    @Column(length = 1000)
    private String decisionNote;

    @Column(nullable = false)
    private Instant createdAt;

    protected PullRequestReview() {
    }

    public PullRequestReview(
            String tenantId,
            String organizationName,
            String repository,
            int prNumber,
            String title,
            String author,
            String serviceName,
            String ownerTeam,
            String ciStatus,
            List<String> changedFiles,
            Long linkedDeploymentId,
            int riskScore,
            PullRequestRecommendation recommendation,
            String explanation,
            Instant createdAt
    ) {
        this.tenantId = tenantId;
        this.organizationName = organizationName;
        this.repository = repository;
        this.prNumber = prNumber;
        this.title = title;
        this.author = author;
        this.serviceName = serviceName;
        this.ownerTeam = ownerTeam;
        this.ciStatus = ciStatus;
        this.changedFiles = new ArrayList<>(changedFiles);
        this.linkedDeploymentId = linkedDeploymentId;
        this.riskScore = riskScore;
        this.recommendation = recommendation;
        this.explanation = explanation;
        this.createdAt = createdAt;
    }

    public void decide(PullRequestDecision decision, String note) {
        this.decision = decision;
        this.decisionNote = note;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getRepository() {
        return repository;
    }

    public int getPrNumber() {
        return prNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public String getCiStatus() {
        return ciStatus;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public Long getLinkedDeploymentId() {
        return linkedDeploymentId;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public PullRequestRecommendation getRecommendation() {
        return recommendation;
    }

    public String getExplanation() {
        return explanation;
    }

    public PullRequestDecision getDecision() {
        return decision;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
