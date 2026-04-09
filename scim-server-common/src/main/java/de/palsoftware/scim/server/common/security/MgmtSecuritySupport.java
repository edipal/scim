package de.palsoftware.scim.server.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class MgmtSecuritySupport {

    private MgmtSecuritySupport() {
    }

    public static HttpSecurity configureBaseSecurity(HttpSecurity http, String actuatorApiKey) throws Exception {
        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/error").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new ActuatorApiKeyAuthFilter(actuatorApiKey),
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
    }

    @SuppressWarnings("java:S3330")
    public static CookieCsrfTokenRepository csrfTokenRepository(String cookieName) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(cookieName);
        return repository;
    }

    public static void addMappedAuthorities(List<String> roles,
                                            Set<GrantedAuthority> mappedAuthorities,
                                            String adminRole,
                                            String userRole) {
        String normalizedAdminRole = normalizeRole(adminRole);
        String normalizedUserRole = normalizeRole(userRole);
        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (roles == null) {
            return;
        }
        for (String role : roles) {
            String normalized = normalizeRole(role);
            if (normalized == null) {
                continue;
            }
            if (normalizedAdminRole != null && normalized.equals(normalizedAdminRole)) {
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            if (normalizedUserRole != null && normalized.equals(normalizedUserRole)) {
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
        }
    }

    static List<String> extractClaimValues(Object claimValue) {
        if (claimValue == null) {
            return List.of();
        }
        if (claimValue instanceof String value) {
            return List.of(value.split("[,\\s]+"));
        }
        if (claimValue instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object entry : collection) {
                if (entry != null) {
                    values.add(entry.toString());
                }
            }
            return values;
        }
        if (claimValue.getClass().isArray()) {
            int length = Array.getLength(claimValue);
            List<String> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                Object entry = Array.get(claimValue, index);
                if (entry != null) {
                    values.add(entry.toString());
                }
            }
            return values;
        }
        return List.of(claimValue.toString());
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }
        return normalized;
    }
}