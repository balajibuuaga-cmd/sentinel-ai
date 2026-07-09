package com.sentinelai.model.account;

public record MfaEnrollResponse(String secret, String otpauthUrl) {
}
