package de.palsoftware.scim.server.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public final class AzureOidcSecuritySupport {

    private AzureOidcSecuritySupport() {
    }

    public static OidcUserService createOidcUserService(String roleClaim,
                                                        String adminRole,
                                                        String userRole,
                                                        BiConsumer<String, String> userProvisioner) {
        OidcUserService delegate = new OidcUserService();
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

                String sub = oidcUser.getSubject();
                String email = resolveEmail(oidcUser);
                if (sub != null && !sub.isBlank()) {
                    userProvisioner.accept(sub, email);
                }

                return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
            }
        };
    }

    private static String resolveEmail(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        if (email != null && !email.isBlank()) return email;
        String upn = oidcUser.getClaimAsString("upn");
        if (upn != null && !upn.isBlank()) return upn;
        String unique = oidcUser.getClaimAsString("unique_name");
        if (unique != null && !unique.isBlank()) return unique;
        return oidcUser.getPreferredUsername();
    }
}