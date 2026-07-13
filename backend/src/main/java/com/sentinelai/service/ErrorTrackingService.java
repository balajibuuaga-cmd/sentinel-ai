package com.sentinelai.service;

import com.sentinelai.model.ErrorEvent;
import com.sentinelai.model.intelligence.ErrorEventView;
import com.sentinelai.observability.RequestContext;
import com.sentinelai.repository.ErrorEventRepository;
import com.sentinelai.security.TenantContext;
import com.sentinelai.security.TenantIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * In-app error tracking: records unhandled server errors so operators can see
 * what's breaking without an external service (Sentry etc.). Recording never
 * throws and runs in its own transaction, so it can never turn a 500 into a
 * worse failure or interfere with the request that produced it.
 */
@Service
public class ErrorTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ErrorTrackingService.class);
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_PATH = 500;

    private final ErrorEventRepository errorEventRepository;
    private final TenantContext tenantContext;

    public ErrorTrackingService(ErrorEventRepository errorEventRepository, TenantContext tenantContext) {
        this.errorEventRepository = errorEventRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Throwable ex, String path, String httpMethod) {
        try {
            TenantIdentity tenant = tenantContext.current();
            errorEventRepository.save(new ErrorEvent(
                    tenant.tenantId(),
                    tenant.organizationName(),
                    ex.getClass().getName(),
                    truncate(ex.getMessage(), MAX_MESSAGE),
                    truncate(path, MAX_PATH),
                    httpMethod,
                    RequestContext.requestId(),
                    Instant.now()
            ));
        } catch (Exception recordingFailure) {
            log.warn("Failed to record error event", recordingFailure);
        }
    }

    public List<ErrorEventView> recent() {
        return errorEventRepository.findTop50ByTenantIdOrderByOccurredAtDesc(tenantContext.tenantId()).stream()
                .map(e -> new ErrorEventView(
                        e.getErrorType(),
                        e.getMessage(),
                        e.getPath(),
                        e.getHttpMethod(),
                        e.getRequestId(),
                        e.getOccurredAt()
                ))
                .toList();
    }

    public long last24hCount() {
        return errorEventRepository.countByTenantIdAndOccurredAtAfter(
                tenantContext.tenantId(),
                Instant.now().minus(24, ChronoUnit.HOURS)
        );
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
