package com.sentinelai.service;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.User;
import com.sentinelai.model.account.AccountProfileView;
import com.sentinelai.model.account.ChangePasswordRequest;
import com.sentinelai.model.account.MfaCodeRequest;
import com.sentinelai.model.account.MfaDisableRequest;
import com.sentinelai.model.account.MfaEnrollResponse;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.UserRepository;
import com.sentinelai.security.PasswordPolicy;
import com.sentinelai.security.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenantContext;
    private final TotpService totpService;

    public AccountService(
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            TenantContext tenantContext,
            TotpService totpService
    ) {
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantContext = tenantContext;
        this.totpService = totpService;
    }

    public AccountProfileView current() {
        User user = currentUser();
        return new AccountProfileView(
                user.getEmail(),
                user.getRole(),
                user.getTenantId(),
                user.getOrganizationName(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.isMfaEnabled()
        );
    }

    public MfaEnrollResponse enrollMfa() {
        User user = currentUser();
        String secret = totpService.generateSecret();
        user.startMfaEnrollment(secret);
        userRepository.save(user);
        audit(user, "MFA_ENROLL_STARTED", "MFA enrollment started; awaiting confirmation code.");
        return new MfaEnrollResponse(secret, totpService.otpauthUrl(secret, user.getEmail()));
    }

    public void confirmMfa(MfaCodeRequest request) {
        User user = currentUser();
        if (user.getPendingMfaSecret() == null) {
            throw new IllegalArgumentException("No MFA enrollment is in progress. Start enrollment first.");
        }
        if (!totpService.verifyCode(user.getPendingMfaSecret(), request.code())) {
            throw new IllegalArgumentException("Incorrect verification code. Please try again.");
        }
        user.confirmMfaEnrollment();
        userRepository.save(user);
        audit(user, "MFA_ENABLED", "Two-factor authentication was enabled.");
    }

    public void disableMfa(MfaDisableRequest request) {
        User user = currentUser();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        user.disableMfa();
        userRepository.save(user);
        audit(user, "MFA_DISABLED", "Two-factor authentication was disabled.");
    }

    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        PasswordPolicy.validate(request.newPassword());
        user.applyPasswordReset(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        audit(user, "PASSWORD_CHANGED", "Password changed from account settings.");
    }

    private User currentUser() {
        return userRepository.findByEmail(tenantContext.currentUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));
    }

    private void audit(User user, String action, String details) {
        auditEventRepository.save(new AuditEvent(
                user.getTenantId(),
                user.getOrganizationName(),
                user.getEmail(),
                action,
                "user:" + user.getEmail(),
                details,
                Instant.now()
        ));
    }
}
