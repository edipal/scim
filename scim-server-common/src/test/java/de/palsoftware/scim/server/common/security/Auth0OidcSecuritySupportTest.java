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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Auth0OidcSecuritySupportTest {

    private static final String ROLE_CLAIM = "https://scimplayground.dev/roles";
    private static final String ADMIN_ROLE = "admin";
    private static final String USER_ROLE = "user";

    @Test
    void loadUser_emailPresent_provisionsAndReturns() {
        OidcUser upstream = oidcUser(Map.of("sub", "auth0|123", "email", "user@example.com"));
        AtomicReference<String> provisioned = new AtomicReference<>();

        OidcUserService service = Auth0OidcSecuritySupport.createOidcUserService(
                ROLE_CLAIM, ADMIN_ROLE, USER_ROLE, provisioned::set, stubDelegate(upstream));

        OidcUser result = service.loadUser(mock(OidcUserRequest.class));

        assertThat(provisioned.get()).isEqualTo("user@example.com");
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER");
    }

    @Test
    void loadUser_adminRole_grantsAdminAuthority() {
        OidcUser upstream = oidcUser(Map.of(
                "sub", "auth0|456",
                "email", "admin@example.com",
                ROLE_CLAIM, List.of(ADMIN_ROLE)));
        AtomicReference<String> provisioned = new AtomicReference<>();

        OidcUserService service = Auth0OidcSecuritySupport.createOidcUserService(
                ROLE_CLAIM, ADMIN_ROLE, USER_ROLE, provisioned::set, stubDelegate(upstream));

        OidcUser result = service.loadUser(mock(OidcUserRequest.class));

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void loadUser_noEmail_throws() {
        OidcUser upstream = oidcUser(Map.of("sub", "auth0|789"));

        OidcUserService service = Auth0OidcSecuritySupport.createOidcUserService(
                ROLE_CLAIM, ADMIN_ROLE, USER_ROLE, email -> {}, stubDelegate(upstream));

        assertThatThrownBy(() -> service.loadUser(mock(OidcUserRequest.class)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private static OidcUser oidcUser(Map<String, Object> claims) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("token-value", now, now.plusSeconds(3600), claims);
        return new DefaultOidcUser(Collections.emptyList(), idToken);
    }

    private static OidcUserService stubDelegate(OidcUser result) {
        OidcUserService delegate = mock(OidcUserService.class);
        when(delegate.loadUser(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        return delegate;
    }
}
