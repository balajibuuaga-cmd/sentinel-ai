package com.sentinelai.service;

public interface EmailService {
    void send(String to, String subject, String bodyText);
}
