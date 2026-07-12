package com.sentinelai.service;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.User;
import com.sentinelai.model.team.TeamInviteRequest;
import com.sentinelai.model.team.TeamMemberView;
import com.sentinelai.model.team.TeamRoleUpdateRequest;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.repository.UserRepository;
import com.sentinelai.security.TenantContext;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TeamService {

    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "RELEASE_MANAGER", "VIEWER");
    private static final Duration INVITE_TOKEN_VALIDITY = Duration.ofDays(7);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TenantContext tenantContext;
    private final String resetLinkBaseUrl;

    public TeamService(
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            TenantContext tenantContext,
            @Value("${sentinel.email.reset-link-base-url}") String resetLinkBaseUrl
    ) {
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tenantContext = tenantContext;
        this.resetLinkBaseUrl = resetLinkBaseUrl;
    }

    public List<TeamMemberView> listMembers() {
        String currentUsername = tenantContext.currentUsername();
        return userRepository.findByTenantIdOrderByCreatedAtAsc(tenantContext.tenantId()).stream()
                .map(user -> toView(user, currentUsername))
                .toList();
    }

    public TeamMemberView invite(TeamInviteRequest request) {
        validateRole(request.role());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = new User(
                tenantContext.tenantId(),
                tenantContext.organizationName(),
                email,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                request.role(),
                Instant.now()
        );

        String rawToken = generateRawToken();
        user.issuePasswordResetToken(hashToken(rawToken), INVITE_TOKEN_VALIDITY);
        user = userRepository.save(user);
        audit(user, "TEAM_MEMBER_INVITED", tenantContext.currentUsername() + " invited " + email + " as " + request.role() + ".");

        String inviteLink = resetLinkBaseUrl + "/reset-password?token=" + rawToken;
        emailService.send(
                email,
                "You're invited to join " + tenantContext.organizationName() + " on Sentinel AI",
                "You've been invited to join " + tenantContext.organizationName() + " on Sentinel AI.\n\n"
                        + "Set your password to get started (link expires in 7 days): " + inviteLink
        );

        return toView(user, tenantContext.currentUsername());
    }

    public TeamMemberView updateRole(Long memberId, TeamRoleUpdateRequest request) {
        validateRole(request.role());
        User user = findInTenant(memberId);
        if (isCurrentUser(user)) {
            throw new IllegalArgumentException("You cannot change your own role.");
        }
        user.changeRole(request.role());
        user = userRepository.save(user);
        audit(user, "TEAM_MEMBER_ROLE_CHANGED", tenantContext.currentUsername() + " changed " + user.getEmail() + "'s role to " + request.role() + ".");
        return toView(user, tenantContext.currentUsername());
    }

    public void removeMember(Long memberId) {
        User user = findInTenant(memberId);
        if (isCurrentUser(user)) {
            throw new IllegalArgumentException("You cannot remove your own account.");
        }
        audit(user, "TEAM_MEMBER_REMOVED", tenantContext.currentUsername() + " removed " + user.getEmail() + " from the team.");
        userRepository.delete(user);
    }

    private User findInTenant(Long memberId) {
        return userRepository.findByIdAndTenantId(memberId, tenantContext.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Team member not found."));
    }

    private boolean isCurrentUser(User user) {
        return user.getEmail().equalsIgnoreCase(tenantContext.currentUsername());
    }

    private void validateRole(String role) {
        if (!VALID_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role must be one of " + VALID_ROLES + ".");
        }
    }

    private TeamMemberView toView(User user, String currentUsername) {
        return new TeamMemberView(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isLocked(),
                user.getEmail().equalsIgnoreCase(currentUsername),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
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

    private void audit(User user, String action, String details) {
        auditEventRepository.save(new AuditEvent(
                user.getTenantId(),
                user.getOrganizationName(),
                tenantContext.currentUsername(),
                action,
                "user:" + user.getEmail(),
                details,
                Instant.now()
        ));
    }
}
