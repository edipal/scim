package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudflareJwtSecuritySupportTest {

    @Test
    void jwtAuthenticationConverter_mapsAdminRoleFromNestedCustomClaim() {
        AtomicReference<String> provisionedEmail = new AtomicReference<>();
        Jwt jwt = jwt(Map.of(
                "sub", "user-123",
                "email", "USER@example.com",
                "custom", Map.of("https://scimsandbox.net/roles", new String[]{"admin"})
        ));

        AbstractAuthenticationToken authentication = CloudflareJwtSecuritySupport
                .createJwtAuthenticationConverter(
                        "https://scimsandbox.net/roles",
                        "admin",
                        "user",
                        provisionedEmail::set)
                .convert(jwt);

        assertThat(provisionedEmail.get()).isEqualTo("user@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void jwtAuthenticationConverter_mapsAdminRoleFromTopLevelClaim() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-123",
                "email", "user@example.com",
                "roles", new String[]{"admin"}
        ));

        AbstractAuthenticationToken authentication = CloudflareJwtSecuritySupport
                .createJwtAuthenticationConverter("roles", "admin", "user", email -> { })
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void jwtAuthenticationConverter_withoutEmailRejectsLogin() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-123",
                "preferred_username", "not-an-email",
                "roles", new String[]{"user"}
        ));
        var converter = CloudflareJwtSecuritySupport
                .createJwtAuthenticationConverter("roles", "admin", "user", email -> { });

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("email claim is required");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Instant issuedAt = Instant.now();
        return new Jwt("token-value", issuedAt, issuedAt.plusSeconds(3600), Map.of("alg", "none"), claims);
    }
}