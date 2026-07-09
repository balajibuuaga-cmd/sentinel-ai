package com.sentinelai.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    public static final String DEFAULT_TENANT_ID = "sentinel-demo";
    public static final String DEFAULT_ORGANIZATION_NAME = "Sentinel Demo";

    public TenantIdentity current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof TenantIdentity tenantIdentity) {
            return tenantIdentity;
        }
        return new TenantIdentity(DEFAULT_TENANT_ID, DEFAULT_ORGANIZATION_NAME);
    }

    public String tenantId() {
        return current().tenantId();
    }

    public String organizationName() {
        return current().organizationName();
    }
}
