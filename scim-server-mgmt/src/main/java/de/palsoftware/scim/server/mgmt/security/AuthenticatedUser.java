package de.palsoftware.scim.server.mgmt.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static String username(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Missing authentication");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            String resolved = resolveOidcUsername(oidcUser);
            if (resolved != null) {
                return resolved;
            }
        }
        if (principal instanceof Jwt jwt) {
            String resolved = resolveJwtUsername(jwt);
            if (resolved != null) {
                return resolved;
            }
        }
        String fallback = authentication.getName();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("Unable to resolve authenticated username");
        }
        return fallback;
    }

    public static String userId(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Missing authentication");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            String sub = oidcUser.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
        }
        if (principal instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
        }
        String fallback = authentication.getName();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("Unable to resolve authenticated user id");
        }
        return fallback;
    }

    private static String resolveOidcUsername(OidcUser oidcUser) {
        String preferredUsername = oidcUser.getPreferredUsername();
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        String upn = oidcUser.getClaimAsString("upn");
        if (upn != null && !upn.isBlank()) {
            return upn;
        }
        String email = oidcUser.getEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }
        String sub = oidcUser.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        return null;
    }

    private static String resolveJwtUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        String upn = jwt.getClaimAsString("upn");
        if (upn != null && !upn.isBlank()) {
            return upn;
        }
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        return null;
    }

    public static String displayName(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            String email = oidcUser.getEmail();
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        if (principal instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return username(authentication);
    }

    public static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
