package de.palsoftware.scim.server.common.security;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Locale;
import java.util.Map;

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

    public static String resolveEmail(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return normalizeEmail(firstNonBlank(
                claimAsString(jwt, "email"),
                claimAsString(jwt, "upn"),
                claimAsString(jwt, "unique_name"),
                claimAsString(jwt, "preferred_username")));
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

    private static String claimAsString(Jwt jwt, String claimName) {
        String directClaim = jwt.getClaimAsString(claimName);
        if (directClaim != null && !directClaim.isBlank()) {
            return directClaim;
        }
        Object customClaim = jwt.getClaim("custom");
        if (customClaim instanceof Map<?, ?> customClaims) {
            Object nestedClaim = customClaims.get(claimName);
            if (nestedClaim instanceof String nestedString && !nestedString.isBlank()) {
                return nestedString;
            }
        }
        return null;
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