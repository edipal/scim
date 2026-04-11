package de.palsoftware.scim.server.common.security;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Locale;

public final class PrincipalEmailSupport {

    private PrincipalEmailSupport() {
    }

    public static String resolveEmail(OidcUser oidcUser) {
        if (oidcUser == null) {
            return null;
        }
        return normalizeEmail(firstNonBlank(
                oidcUser.getEmail(),
                oidcUser.getClaimAsString("upn"),
                oidcUser.getClaimAsString("unique_name"),
                oidcUser.getPreferredUsername()));
    }

    public static String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !normalized.contains("@")) {
            return null;
        }
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}