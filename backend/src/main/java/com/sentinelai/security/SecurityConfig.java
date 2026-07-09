package com.sentinelai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            SecurityHeadersFilter securityHeadersFilter,
            ApiModeFilter apiModeFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password-reset/request").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cognito/exchange").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/mfa/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/github").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/deployments/*/approval").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/deployments/*/analyze").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/pr-reviews/simulate").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/pr-reviews/*/decision").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/architecture/import").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/incidents/*/status").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/integrations/*/simulate").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/integration-connections/*/install").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/integration-connections/*/sync").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/integration-connections/*").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/jobs/*/retry").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/deliveries/*/replay").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/team/invite").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/team/members/*/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/team/members/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/operator/**").hasAnyRole("ADMIN", "RELEASE_MANAGER")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "RELEASE_MANAGER", "VIEWER")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(apiModeFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(securityHeadersFilter, HeaderWriterFilter.class)
                .build();
    }
}
