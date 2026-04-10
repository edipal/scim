package de.palsoftware.scim.validator.mgmt.security;

import de.palsoftware.scim.server.common.security.PrincipalEmailSupport;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static String email(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Missing authentication");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            String resolved = PrincipalEmailSupport.resolveEmail(oidcUser);
            if (resolved != null) {
                return resolved;
            }
            throw new IllegalStateException("Authenticated principal is missing an email address");
        }
        if (principal instanceof Jwt jwt) {
            String resolved = PrincipalEmailSupport.resolveEmail(jwt);
            if (resolved != null) {
                return resolved;
            }
            throw new IllegalStateException("Authenticated principal is missing an email address");
        }
        String fallback = authentication.getName();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("Unable to resolve authenticated email");
        }
        return fallback;
    }

    public static String displayName(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return email(authentication);
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
