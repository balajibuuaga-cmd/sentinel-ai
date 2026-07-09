package com.sentinelai.service;

import com.sentinelai.model.ArchitectureBrain;
import com.sentinelai.model.ArchitectureDependency;
import com.sentinelai.model.ArchitectureDependencyType;
import com.sentinelai.model.ArchitectureImportRequest;
import com.sentinelai.model.ArchitectureRisk;
import com.sentinelai.model.ArchitectureRiskType;
import com.sentinelai.model.ArchitectureServiceNode;
import com.sentinelai.model.ArchitectureSeverity;
import com.sentinelai.repository.ArchitectureDependencyRepository;
import com.sentinelai.repository.ArchitectureRiskRepository;
import com.sentinelai.repository.ArchitectureServiceRepository;
import com.sentinelai.security.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ArchitectureBrainService {

    private final ArchitectureServiceRepository serviceRepository;
    private final ArchitectureDependencyRepository dependencyRepository;
    private final ArchitectureRiskRepository riskRepository;
    private final TenantContext tenantContext;

    public ArchitectureBrainService(
            ArchitectureServiceRepository serviceRepository,
            ArchitectureDependencyRepository dependencyRepository,
            ArchitectureRiskRepository riskRepository,
            TenantContext tenantContext
    ) {
        this.serviceRepository = serviceRepository;
        this.dependencyRepository = dependencyRepository;
        this.riskRepository = riskRepository;
        this.tenantContext = tenantContext;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (!serviceRepository.findByTenantId(TenantContext.DEFAULT_TENANT_ID).isEmpty()) {
            return;
        }
        importArchitecture(new ArchitectureImportRequest(
                List.of(
                        new ArchitectureImportRequest.ServiceInput("payment-api", "Payments Platform", "Java 17 / Spring Boot", "tier-1", "sentinel-ai/payment-api", "Authorizes payments and writes settlement events."),
                        new ArchitectureImportRequest.ServiceInput("checkout-service", "Commerce Experience", "Node.js", "tier-1", "sentinel-ai/checkout-service", "Coordinates carts, payment authorization, and order placement."),
                        new ArchitectureImportRequest.ServiceInput("billing-service", "Payments Platform", "Java 17", "tier-1", "sentinel-ai/billing-service", "Invoices customers and reconciles billing state."),
                        new ArchitectureImportRequest.ServiceInput("customer-ledger", "Finance Systems", "PostgreSQL", "tier-1", "sentinel-ai/customer-ledger", "System of record for customer balance and ledger writes."),
                        new ArchitectureImportRequest.ServiceInput("inventory-sync", "Commerce Operations", "Kotlin", "tier-2", "sentinel-ai/inventory-sync", "Synchronizes warehouse availability and reservations."),
                        new ArchitectureImportRequest.ServiceInput("fraud-screening", "Risk Platform", "Python", "tier-1", "sentinel-ai/fraud-screening", "Scores payment fraud and risky checkout behavior.")
                ),
                List.of(
                        new ArchitectureImportRequest.DependencyInput("checkout-service", "payment-api", ArchitectureDependencyType.API, "critical", "Checkout blocks on payment authorization."),
                        new ArchitectureImportRequest.DependencyInput("payment-api", "customer-ledger", ArchitectureDependencyType.DATABASE, "critical", "Payment writes settlement and balance records."),
                        new ArchitectureImportRequest.DependencyInput("billing-service", "customer-ledger", ArchitectureDependencyType.DATABASE, "critical", "Billing shares ledger write paths."),
                        new ArchitectureImportRequest.DependencyInput("payment-api", "fraud-screening", ArchitectureDependencyType.API, "high", "Payment authorization depends on fraud decisioning."),
                        new ArchitectureImportRequest.DependencyInput("checkout-service", "inventory-sync", ArchitectureDependencyType.API, "high", "Checkout reads availability before taking payment."),
                        new ArchitectureImportRequest.DependencyInput("inventory-sync", "checkout-service", ArchitectureDependencyType.QUEUE, "medium", "Inventory emits reservation updates consumed by checkout.")
                )
        ));
    }

    @Transactional
    public ArchitectureBrain importArchitecture(ArchitectureImportRequest request) {
        for (ArchitectureImportRequest.ServiceInput service : request.services()) {
            serviceRepository.findByTenantIdAndServiceName(tenantContext.tenantId(), service.serviceName())
                    .ifPresentOrElse(
                            existing -> {
                                existing.update(
                                        defaultString(service.ownerTeam(), "Unassigned"),
                                        defaultString(service.runtime(), "unknown"),
                                        defaultString(service.tier(), "tier-unknown"),
                                        defaultString(service.repository(), "unknown"),
                                        defaultString(service.description(), "No description provided.")
                                );
                                serviceRepository.save(existing);
                            },
                            () -> serviceRepository.save(new ArchitectureServiceNode(
                                    tenantContext.tenantId(),
                                    tenantContext.organizationName(),
                                    service.serviceName(),
                                    defaultString(service.ownerTeam(), "Unassigned"),
                                    defaultString(service.runtime(), "unknown"),
                                    defaultString(service.tier(), "tier-unknown"),
                                    defaultString(service.repository(), "unknown"),
                                    defaultString(service.description(), "No description provided.")
                            ))
                    );
        }

        for (ArchitectureImportRequest.DependencyInput dependency : request.dependencies()) {
            if (!dependencyRepository.existsByTenantIdAndSourceServiceAndTargetService(
                    tenantContext.tenantId(),
                    dependency.sourceService(),
                    dependency.targetService()
            )) {
                dependencyRepository.save(new ArchitectureDependency(
                        tenantContext.tenantId(),
                        tenantContext.organizationName(),
                        dependency.sourceService(),
                        dependency.targetService(),
                        dependency.dependencyType() == null ? ArchitectureDependencyType.API : dependency.dependencyType(),
                        defaultString(dependency.criticality(), "medium"),
                        defaultString(dependency.notes(), "Imported architecture dependency.")
                ));
            }
        }

        detectRisks();
        return brain();
    }

    @Transactional(readOnly = true)
    public List<ArchitectureServiceNode> services() {
        return serviceRepository.findByTenantId(tenantContext.tenantId()).stream()
                .sorted(Comparator.comparing(ArchitectureServiceNode::getServiceName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArchitectureDependency> dependencies() {
        return dependencyRepository.findByTenantId(tenantContext.tenantId());
    }

    @Transactional(readOnly = true)
    public List<ArchitectureRisk> risks() {
        return riskRepository.findByTenantId(tenantContext.tenantId()).stream()
                .sorted(Comparator.comparing(ArchitectureRisk::getSeverity).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public ArchitectureBrain brain() {
        List<ArchitectureServiceNode> services = services();
        List<ArchitectureDependency> dependencies = dependencies();
        List<ArchitectureRisk> risks = risks();
        ArchitectureRisk highest = risks.stream().findFirst().orElse(null);
        String recommendedRefactor = highest == null
                ? "No urgent architecture refactor detected."
                : highest.getRecommendation();

        return new ArchitectureBrain(
                "I mapped " + services.size() + " services and " + dependencies.size()
                        + " dependency edges. I found " + risks.size()
                        + " architecture risks that can affect deployment judgment.",
                recommendedRefactor,
                services.size(),
                dependencies.size(),
                risks.size(),
                services,
                dependencies,
                risks
        );
    }

    @Transactional
    public void detectRisks() {
        riskRepository.deleteByTenantId(tenantContext.tenantId());
        List<ArchitectureServiceNode> services = serviceRepository.findByTenantId(tenantContext.tenantId());
        List<ArchitectureDependency> dependencies = dependencyRepository.findByTenantId(tenantContext.tenantId());
        List<ArchitectureRisk> risks = new ArrayList<>();

        Map<String, Long> inbound = dependencies.stream()
                .collect(Collectors.groupingBy(ArchitectureDependency::getTargetService, Collectors.counting()));

        for (ArchitectureServiceNode service : services) {
            long inboundCount = inbound.getOrDefault(service.getServiceName(), 0L);
            if (inboundCount >= 2) {
                risks.add(new ArchitectureRisk(
                        tenantContext.tenantId(),
                        tenantContext.organizationName(),
                        service.getServiceName(),
                        ArchitectureRiskType.HIGH_BLAST_RADIUS,
                        inboundCount >= 3 ? ArchitectureSeverity.CRITICAL : ArchitectureSeverity.HIGH,
                        service.getServiceName() + " has " + inboundCount + " inbound dependencies.",
                        "Reduce direct coupling or add contract tests and deployment gates around " + service.getServiceName() + "."
                ));
            }
            if ("Unassigned".equalsIgnoreCase(service.getOwnerTeam())) {
                risks.add(new ArchitectureRisk(
                        tenantContext.tenantId(),
                        tenantContext.organizationName(),
                        service.getServiceName(),
                        ArchitectureRiskType.MISSING_OWNER,
                        ArchitectureSeverity.HIGH,
                        service.getServiceName() + " has no accountable owner team.",
                        "Assign ownership before allowing production-impacting changes."
                ));
            }
        }

        for (ArchitectureDependency dependency : dependencies) {
            if (dependency.getDependencyType() == ArchitectureDependencyType.DATABASE
                    && "critical".equalsIgnoreCase(dependency.getCriticality())) {
                risks.add(new ArchitectureRisk(
                        tenantContext.tenantId(),
                        tenantContext.organizationName(),
                        dependency.getTargetService(),
                        ArchitectureRiskType.SHARED_DATABASE,
                        ArchitectureSeverity.HIGH,
                        dependency.getSourceService() + " writes or reads critical state from " + dependency.getTargetService() + ".",
                        "Move toward explicit service APIs or add migration approvals for shared database changes."
                ));
            }
            if (dependencyRepository.existsByTenantIdAndSourceServiceAndTargetService(
                    tenantContext.tenantId(),
                    dependency.getTargetService(),
                    dependency.getSourceService()
            )) {
                risks.add(new ArchitectureRisk(
                        tenantContext.tenantId(),
                        tenantContext.organizationName(),
                        dependency.getSourceService(),
                        ArchitectureRiskType.CIRCULAR_DEPENDENCY,
                        ArchitectureSeverity.CRITICAL,
                        dependency.getSourceService() + " and " + dependency.getTargetService() + " depend on each other.",
                        "Break the cycle with an event contract or ownership boundary."
                ));
            }
        }

        riskRepository.saveAll(risks);
    }

    public String answerArchitectureQuestion(String normalizedQuestion) {
        ArchitectureBrain brain = brain();
        ArchitectureRisk highest = brain.risks().stream().findFirst().orElse(null);

        if (normalizedQuestion.contains("depends on")) {
            String target = extractServiceName(normalizedQuestion);
            List<ArchitectureDependency> inbound = dependencyRepository.findByTenantIdAndTargetService(tenantContext.tenantId(), target);
            if (!inbound.isEmpty()) {
                return target + " is depended on by "
                        + inbound.stream().map(ArchitectureDependency::getSourceService).distinct().collect(Collectors.joining(", "))
                        + ".";
            }
        }

        if (normalizedQuestion.contains("fragile") || normalizedQuestion.contains("blast radius") || normalizedQuestion.contains("architecture")) {
            return highest == null
                    ? brain.summary()
                    : "Architecture Brain says " + highest.getServiceName() + " is the most fragile area: "
                    + highest.getExplanation() + " Recommendation: " + highest.getRecommendation();
        }

        if (normalizedQuestion.contains("refactor")) {
            return brain.recommendedRefactor();
        }

        return brain.summary();
    }

    private String extractServiceName(String question) {
        for (ArchitectureServiceNode service : serviceRepository.findByTenantId(tenantContext.tenantId())) {
            if (question.contains(service.getServiceName().toLowerCase(Locale.US))) {
                return service.getServiceName();
            }
        }
        return "checkout-service";
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
