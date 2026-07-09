package com.sentinelai.security;

public record AuthModeStatus(
        String mode,
        boolean demoLoginEnabled,
        boolean cognitoConfigured,
        String cognitoIssuer,
        String cognitoAudience,
        String cognitoClientId,
        String cognitoHostedUiBaseUrl,
        String cognitoLoginUrl,
        String cognitoLogoutUrl,
        String cognitoRedirectUri,
        String roleClaim,
        String tenantIdClaim,
        String organizationNameClaim
) {
}
