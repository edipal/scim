package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureOidcSecuritySupportTest {

    @Test
    void oidcUserService_provisionsNormalizedEmailAndMapsRoles() {
        AtomicReference<String> provisionedEmail = new AtomicReference<>();
        OidcUser oidcUser = oidcUser(Map.of(
                "sub", "user-123",
                "roles", List.of("admin"),
                "email", " User@Example.com "
        ));
        OidcUserService delegate = mock(OidcUserService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        when(delegate.loadUser(request)).thenReturn(oidcUser);

        OidcUser loadedUser = AzureOidcSecuritySupport.createOidcUserService(
                "roles",
                "admin",
                "user",
                provisionedEmail::set,
                delegate)
                .loadUser(request);

        assertThat(provisionedEmail.get()).isEqualTo("user@example.com");
        assertThat(loadedUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void oidcUserService_withoutEmailRejectsLogin() {
        OidcUser oidcUser = oidcUser(Map.of(
                "sub", "user-123",
                "roles", List.of("user"),
                "preferred_username", "not-an-email"
        ));
        OidcUserService delegate = mock(OidcUserService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        OidcUserService oidcUserService = AzureOidcSecuritySupport.createOidcUserService(
                "roles",
                "admin",
                "user",
                email -> { },
                delegate);

        assertThatThrownBy(() -> oidcUserService.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("email claim is required");
    }

    private static OidcUser oidcUser(Map<String, Object> claims) {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken("token-value", issuedAt, issuedAt.plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(), idToken);
    }
}