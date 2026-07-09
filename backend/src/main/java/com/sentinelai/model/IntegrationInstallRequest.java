package com.sentinelai.model;

public record IntegrationInstallRequest(String externalAccount, String code, String state) {
}
