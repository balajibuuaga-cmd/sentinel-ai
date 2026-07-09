package com.sentinelai.controller;

import com.sentinelai.model.account.AccountProfileView;
import com.sentinelai.model.account.ChangePasswordRequest;
import com.sentinelai.model.account.MfaCodeRequest;
import com.sentinelai.model.account.MfaDisableRequest;
import com.sentinelai.model.account.MfaEnrollResponse;
import com.sentinelai.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountProfileView current() {
        return accountService.current();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/enroll")
    public MfaEnrollResponse enrollMfa() {
        return accountService.enrollMfa();
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<Void> confirmMfa(@Valid @RequestBody MfaCodeRequest request) {
        accountService.confirmMfa(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Void> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
        accountService.disableMfa(request);
        return ResponseEntity.noContent().build();
    }
}
