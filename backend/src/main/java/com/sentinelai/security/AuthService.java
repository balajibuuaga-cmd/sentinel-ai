package com.sentinelai.security;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.User;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.UserRepository;
import com.sentinelai.service.EmailService;
import com.sentinelai.service.MfaChallengeStore;
import com.sentinelai.service.TotpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final Duration RESET_TOKEN_VALIDITY = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenAuthenticationService tokenAuthenticationService;
    private final EmailService emailService;
    private final String resetLinkBaseUrl;
    private final TotpService totpService;
    private final MfaChallengeStore mfaChallengeStore;

    public AuthService(
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenAuthenticationService tokenAuthenticationService,
            EmailService emailService,
            @Value("${sentinel.email.reset-link-base-url}") String resetLinkBaseUrl,
            TotpService totpService,
            MfaChallengeStore mfaChallengeStore
    ) {
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenAuthenticationService = tokenAuthenticationService;
        this.emailService = emailService;
        this.resetLinkBaseUrl = resetLinkBaseUrl;
        this.totpService = totpService;
        this.mfaChallengeStore = mfaChallengeStore;
    }

    public Optional<LoginResult> login(LoginRequest request) {
        if (!tokenAuthenticationService.demoLoginEnabled()) {
            return Optional.empty();
        }

        Optional<User> maybeUser = userRepository.findByEmail(request.username());
        if (maybeUser.isEmpty()) {
            return Optional.empty();
        }
        User user = maybeUser.get();

        if (user.isLocked()) {
            audit(user, "LOGIN_BLOCKED_LOCKED", "Login rejected: account is temporarily locked.");
            throw new IllegalArgumentException("Account is temporarily locked due to repeated failed logins. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION);
            userRepository.save(user);
            audit(user, "LOGIN_FAILED", user.isLocked()
                    ? "Login failed: incorrect password. Account is now locked."
                    : "Login failed: incorrect password.");
            return Optional.empty();
        }

        if (user.isMfaEnabled()) {
            String challengeToken = mfaChallengeStore.issueChallenge(user.getEmail());
            audit(user, "LOGIN_MFA_CHALLENGE_ISSUED", "Password verified; awaiting MFA code.");
            return Optional.of(LoginResult.mfaRequired(challengeToken));
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);
        audit(user, "LOGIN_SUCCESS", "Login succeeded.");

        return Optional.of(LoginResult.success(toAuthResponse(user)));
    }

    public Optional<AuthResponse> verifyMfaChallenge(MfaChallengeVerifyRequest request) {
        Optional<String> maybeUsername = mfaChallengeStore.resolveAndConsume(request.challengeToken());
        if (maybeUsername.isEmpty()) {
            throw new IllegalArgumentException("This login challenge is invalid or has expired. Please log in again.");
        }
        User user = userRepository.findByEmail(maybeUsername.get())
                .orElseThrow(() -> new IllegalArgumentException("This login challenge is invalid or has expired. Please log in again."));

        if (!totpService.verifyCode(user.getMfaSecret(), request.code())) {
            audit(user, "LOGIN_MFA_FAILED", "Incorrect verification code.");
            return Optional.empty();
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);
        audit(user, "LOGIN_MFA_SUCCESS", "Login succeeded with MFA.");

        return Optional.of(toAuthResponse(user));
    }

    public AuthResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        PasswordPolicy.validate(request.password());

        String tenantId = slugify(request.organizationName());
        User user = new User(
                tenantId,
                request.organizationName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                "ADMIN",
                Instant.now()
        );
        user = userRepository.save(user);
        audit(user, "SIGNUP", "New account created for organization " + user.getOrganizationName() + ".");

        return toAuthResponse(user);
    }

    public void requestPasswordReset(PasswordResetRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            // Do not reveal whether the email is registered.
            return;
        }
        User user = maybeUser.get();

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = hashToken(rawToken);

        user.issuePasswordResetToken(tokenHash, RESET_TOKEN_VALIDITY);
        userRepository.save(user);
        audit(user, "PASSWORD_RESET_REQUESTED", "Password reset link requested.");

        String resetLink = resetLinkBaseUrl + "/reset-password?token=" + rawToken;
        emailService.send(
                user.getEmail(),
                "Reset your Sentinel AI password",
                "We received a request to reset your Sentinel AI password.\n\n"
                        + "Reset it here (expires in 30 minutes): " + resetLink + "\n\n"
                        + "If you didn't request this, you can safely ignore this email."
        );
    }

    public void resetPassword(PasswordResetConfirmRequest request) {
        String tokenHash = hashToken(request.token());
        User user = userRepository.findByResetTokenHash(tokenHash)
                .filter(candidate -> candidate.matchesResetToken(tokenHash))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has expired."));

        PasswordPolicy.validate(request.newPassword());
        user.applyPasswordReset(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        audit(user, "PASSWORD_RESET_COMPLETED", "Password was reset via emailed link.");
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String slugify(String organizationName) {
        String slug = organizationName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "tenant";
        }
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return slug + "-" + suffix;
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

    private AuthResponse toAuthResponse(User user) {
        DemoUser principal = new DemoUser(user.getEmail(), "", user.getRole(), user.getTenantId(), user.getOrganizationName());
        return new AuthResponse(
                jwtService.issue(principal),
                user.getEmail(),
                user.getRole(),
                user.getTenantId(),
                user.getOrganizationName()
        );
    }
}
