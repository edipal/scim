package de.palsoftware.scim.server.mgmt.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedUserTest {

    // ─── username ───────────────────────────────────────────────────────

    @Test
    void username_nullAuth_throws() {
        assertThatThrownBy(() -> AuthenticatedUser.username(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing authentication");
    }

    @Test
    void username_oidcUser_preferredUsername() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getPreferredUsername()).thenReturn("preferred");
        Authentication auth = mockAuthWithPrincipal(oidcUser);

        assertThat(AuthenticatedUser.username(auth)).isEqualTo("preferred");
    }

    @Test
    void username_oidcUser_upnFallback() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getPreferredUsername()).thenReturn(null);
        when(oidcUser.getClaimAsString("upn")).thenReturn("user@domain.com");
        Authentication auth = mockAuthWithPrincipal(oidcUser);

        assertThat(AuthenticatedUser.username(auth)).isEqualTo("user@domain.com");
    }

    @Test
    void username_oidcUser_emailFallback() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getPreferredUsername()).thenReturn(null);
        when(oidcUser.getClaimAsString("upn")).thenReturn(null);
        when(oidcUser.getEmail()).thenReturn("email@test.com");
        Authentication auth = mockAuthWithPrincipal(oidcUser);

        assertThat(AuthenticatedUser.username(auth)).isEqualTo("email@test.com");
    }

    @Test
    void username_oidcUser_subFallback() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getPreferredUsername()).thenReturn(null);
        when(oidcUser.getClaimAsString("upn")).thenReturn(null);
        when(oidcUser.getEmail()).thenReturn(null);
        when(oidcUser.getSubject()).thenReturn("sub-123");
        Authentication auth = mockAuthWithPrincipal(oidcUser);

        assertThat(AuthenticatedUser.username(auth)).isEqualTo("sub-123");
    }

    @Test
    void username_jwt_emailFallback() {
        Authentication auth = new TestingAuthenticationToken(jwt(Map.of(
                "sub", "jwt-sub",
                "email", "jwt@example.com"
        )), "n/a");

        assertThat(AuthenticatedUser.username(auth)).isEqualTo("jwt@example.com");
    }

    @Test
    void username_nonOidc_fallsBackToName() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-oidc");
        when(auth.getName()).thenReturn("fallback-name");

        assertThat(AuthenticatedUser.username(auth)).isEqualTo("fallback-name");
    }

    @Test
    void username_noIdentifier_throws() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-oidc");
        when(auth.getName()).thenReturn(null);

        assertThatThrownBy(() -> AuthenticatedUser.username(auth))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userId_jwtPrincipal_returnsSubject() {
        Authentication auth = new TestingAuthenticationToken(jwt(Map.of(
                "sub", "jwt-sub-123",
                "email", "jwt@example.com"
        )), "n/a");

        assertThat(AuthenticatedUser.userId(auth)).isEqualTo("jwt-sub-123");
    }

    // ─── displayName ────────────────────────────────────────────────────

    @Test
    void displayName_null_returnsNull() {
        assertThat(AuthenticatedUser.displayName(null)).isNull();
    }

    @Test
    void displayName_oidcUser_returnsEmail() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn("display@test.com");
        when(oidcUser.getPreferredUsername()).thenReturn("preferred");
        Authentication auth = mockAuthWithPrincipal(oidcUser);

        assertThat(AuthenticatedUser.displayName(auth)).isEqualTo("display@test.com");
    }

    @Test
    void displayName_oidcUser_noEmail_fallsBackToUsername() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(null);
        when(oidcUser.getPreferredUsername()).thenReturn("preferred");
        Authentication auth = mockAuthWithPrincipal(oidcUser);

        assertThat(AuthenticatedUser.displayName(auth)).isEqualTo("preferred");
    }

    @Test
    void displayName_jwt_returnsEmail() {
        Authentication auth = new TestingAuthenticationToken(jwt(Map.of(
                "sub", "jwt-sub",
                "email", "display@example.com"
        )), "n/a");

        assertThat(AuthenticatedUser.displayName(auth)).isEqualTo("display@example.com");
    }

    // ─── isAdmin ────────────────────────────────────────────────────────

    @Test
    void isAdmin_null_returnsFalse() {
        assertThat(AuthenticatedUser.isAdmin(null)).isFalse();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void isAdmin_withAdminRole_returnsTrue() {
        Authentication auth = mock(Authentication.class);
        Collection<GrantedAuthority>  authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        when(auth.getAuthorities()).thenReturn((Collection) authorities);

        assertThat(AuthenticatedUser.isAdmin(auth)).isTrue();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void isAdmin_withoutAdminRole_returnsFalse() {
        Authentication auth = mock(Authentication.class);
        Collection<GrantedAuthority> authorities = List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER"));
        when(auth.getAuthorities()).thenReturn((Collection) authorities);

        assertThat(AuthenticatedUser.isAdmin(auth)).isFalse();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void isAdmin_noAuthorities_returnsFalse() {
        Authentication auth = mock(Authentication.class);
        Collection<GrantedAuthority> authorities = Collections.<GrantedAuthority>emptyList();
        when(auth.getAuthorities()).thenReturn((Collection) authorities);

        assertThat(AuthenticatedUser.isAdmin(auth)).isFalse();
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private Authentication mockAuthWithPrincipal(Object principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant issuedAt = Instant.now();
        return new Jwt("token-value", issuedAt, issuedAt.plusSeconds(3600), Map.of("alg", "none"), claims);
    }
}
