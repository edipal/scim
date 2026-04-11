package de.palsoftware.scim.server.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class Auth0OidcSecuritySupport {

    private Auth0OidcSecuritySupport() {
    }

    public static OidcUserService createOidcUserService(String roleClaim,
                                                        String adminRole,
                                                        String userRole,
                                                        Consumer<String> userProvisioner) {
        return createOidcUserService(roleClaim, adminRole, userRole, userProvisioner, new OidcUserService());
    }

    static OidcUserService createOidcUserService(String roleClaim,
                                                 String adminRole,
                                                 String userRole,
                                                 Consumer<String> userProvisioner,
                                                 OidcUserService delegate) {
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
                OidcUser oidcUser = delegate.loadUser(userRequest);
                Set<GrantedAuthority> mappedAuthorities = new HashSet<>(oidcUser.getAuthorities());

                MgmtSecuritySupport.addMappedAuthorities(
                        MgmtSecuritySupport.extractClaimValues(oidcUser.getClaim(roleClaim)),
                        mappedAuthorities,
                        adminRole,
                        userRole);

                userProvisioner.accept(requireEmail(oidcUser));

                return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
            }
        };
    }

    private static String requireEmail(OidcUser oidcUser) {
        String email = PrincipalEmailSupport.resolveEmail(oidcUser);
        if (email != null) {
            return email;
        }
        throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_token", "An email claim is required for management access", null));
    }
}
