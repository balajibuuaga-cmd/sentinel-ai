package com.sentinelai.observability;

import com.sentinelai.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperationalEventLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("sentinel.operations");

    private final TenantContext tenantContext;

    public OperationalEventLogger(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    public void info(String event, Map<String, ?> fields) {
        LOGGER.info(format(event, fields));
    }

    public void warn(String event, Map<String, ?> fields) {
        LOGGER.warn(format(event, fields));
    }

    private String format(String event, Map<String, ?> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("requestId", RequestContext.requestId());
        payload.put("tenantId", tenantContext.tenantId());
        payload.put("organizationName", tenantContext.organizationName());
        if (fields != null) {
            payload.putAll(fields);
        }
        return payload.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + quote(entry.getValue()))
                .collect(Collectors.joining(" "));
    }

    private String quote(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
