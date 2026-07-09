package com.sentinelai.controller;

import com.sentinelai.security.AuthModeStatus;
import com.sentinelai.security.TokenAuthenticationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthStatusController {

    private final TokenAuthenticationService tokenAuthenticationService;

    public AuthStatusController(TokenAuthenticationService tokenAuthenticationService) {
        this.tokenAuthenticationService = tokenAuthenticationService;
    }

    @GetMapping("/status")
    public AuthModeStatus status() {
        return tokenAuthenticationService.status();
    }
}
