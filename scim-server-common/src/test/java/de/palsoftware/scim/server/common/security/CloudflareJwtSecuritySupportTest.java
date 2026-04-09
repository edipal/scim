package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudflareJwtSecuritySupportTest {

    @Test
    void jwtAuthenticationConverter_mapsAdminRoleFromNestedCustomClaim() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-123",
                "custom", Map.of("https://scimsandbox.net/roles", new String[]{"admin"})
        ));

        AbstractAuthenticationToken authentication = CloudflareJwtSecuritySupport
                .createJwtAuthenticationConverter("https://scimsandbox.net/roles", "admin", "user")
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void jwtAuthenticationConverter_mapsAdminRoleFromTopLevelClaim() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-123",
                "roles", new String[]{"admin"}
        ));

        AbstractAuthenticationToken authentication = CloudflareJwtSecuritySupport
                .createJwtAuthenticationConverter("roles", "admin", "user")
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Instant issuedAt = Instant.now();
        return new Jwt("token-value", issuedAt, issuedAt.plusSeconds(3600), Map.of("alg", "none"), claims);
    }
}