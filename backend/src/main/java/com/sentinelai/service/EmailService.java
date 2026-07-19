package com.sentinelai.service;

public interface EmailService {

    /**
     * Sends a plain-text email on a best-effort basis.
     *
     * <p>Implementations MUST NOT throw. Callers such as password reset and team
     * invites treat delivery as non-critical: the password-reset endpoint returns
     * an identical response whether or not the address is registered, so a
     * transport failure must never surface as an error or alter the response.
     * Log delivery failures instead of propagating them.
     */
    void send(String to, String subject, String bodyText);
}
