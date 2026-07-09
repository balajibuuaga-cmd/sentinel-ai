package com.sentinelai.controller;

import com.sentinelai.security.AuthResponse;
import com.sentinelai.security.AuthService;
import com.sentinelai.security.CognitoCodeExchangeService;
import com.sentinelai.security.CognitoExchangeRequest;
import com.sentinelai.security.LoginRequest;
import com.sentinelai.security.LoginResult;
import com.sentinelai.security.MfaChallengeVerifyRequest;
import com.sentinelai.security.PasswordResetConfirmRequest;
import com.sentinelai.security.PasswordResetRequest;
import com.sentinelai.security.SignupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CognitoCodeExchangeService cognitoCodeExchangeService;

    public AuthController(AuthService authService, CognitoCodeExchangeService cognitoCodeExchangeService) {
        this.authService = authService;
        this.cognitoCodeExchangeService = cognitoCodeExchangeService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfaChallenge(@Valid @RequestBody MfaChallengeVerifyRequest request) {
        return authService.verifyMfaChallenge(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cognito/exchange")
    public ResponseEntity<AuthResponse> exchangeCognitoCode(@Valid @RequestBody CognitoExchangeRequest request) {
        return cognitoCodeExchangeService.exchange(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }
}
