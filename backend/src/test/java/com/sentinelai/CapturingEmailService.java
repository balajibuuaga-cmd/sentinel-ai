package com.sentinelai;

import com.sentinelai.service.EmailService;

import java.util.concurrent.ConcurrentLinkedDeque;

public class CapturingEmailService implements EmailService {

    public record SentEmail(String to, String subject, String body) {
    }

    public final ConcurrentLinkedDeque<SentEmail> sent = new ConcurrentLinkedDeque<>();

    @Override
    public void send(String to, String subject, String bodyText) {
        sent.add(new SentEmail(to, subject, bodyText));
    }
}
