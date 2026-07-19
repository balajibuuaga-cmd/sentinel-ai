package com.sentinelai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@Service
public class SesEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SesEmailService.class);
    private static final Region AWS_REGION = Region.US_EAST_1;

    private final SesV2Client client;
    private final String fromAddress;

    public SesEmailService(@Value("${sentinel.email.from-address:}") String fromAddress) {
        this.client = SesV2Client.builder()
                .region(AWS_REGION)
                .build();
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String bodyText) {
        if (fromAddress.isBlank()) {
            log.warn("sentinel.email.from-address is not configured; skipping email send to {}", to);
            return;
        }
        try {
            client.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(fromAddress)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).build())
                                    .body(Body.builder().text(Content.builder().data(bodyText).build()).build())
                                    .build())
                            .build())
                    .build());
        } catch (SesV2Exception ex) {
            // Expected in the SES sandbox: sending to an unverified recipient is
            // rejected. Callers (password reset, team invites) must not fail.
            log.warn("SES rejected email to {}: {}", to, ex.getMessage());
        } catch (RuntimeException ex) {
            // Transport-level failures (credentials, network, throttling) are
            // equally non-fatal. Email delivery here is best-effort by design —
            // password reset deliberately returns the same response whether or
            // not the address exists, so a send failure must never change it.
            log.warn("Failed to send email to {}: {}", to, ex.toString());
        }
    }
}
