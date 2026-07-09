package com.sentinelai;

import com.sentinelai.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class EmailTestConfig {

    @Bean
    @Primary
    EmailService emailService() {
        return new CapturingEmailService();
    }
}
