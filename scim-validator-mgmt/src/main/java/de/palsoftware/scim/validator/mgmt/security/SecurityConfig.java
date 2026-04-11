package de.palsoftware.scim.validator.mgmt.security;

import de.palsoftware.scim.server.common.security.Auth0OidcSecuritySupport;
import de.palsoftware.scim.server.common.security.MgmtSecuritySupport;
import de.palsoftware.scim.validator.mgmt.service.MgmtUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSRF_COOKIE_NAME = "SCIM_VALIDATOR_MGMT_XSRF";

    private final String adminRole;
    private final String userRole;
    private final String roleClaim;
    private final String actuatorApiKey;
    private final MgmtUserService mgmtUserService;

    public SecurityConfig(@Value("${app.security.oidc.admin-role}") String adminRole,
                          @Value("${app.security.oidc.user-role}") String userRole,
                          @Value("${app.security.oidc.role-claim}") String roleClaim,
                          @Value("${app.security.actuator.api-key}") String actuatorApiKey,
                          @Lazy MgmtUserService mgmtUserService) {
        this.adminRole = adminRole;
        this.userRole = userRole;
        this.roleClaim = roleClaim;
        this.actuatorApiKey = actuatorApiKey;
        this.mgmtUserService = mgmtUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LogoutSuccessHandler oidcLogoutSuccessHandler) throws Exception {
        MgmtSecuritySupport.configureBaseSecurity(http, actuatorApiKey)
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(
                        Auth0OidcSecuritySupport.createOidcUserService(
                                roleClaim,
                                adminRole,
                                userRole,
                                mgmtUserService::provisionUser))))
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler))
            .csrf(csrf -> csrf.csrfTokenRepository(MgmtSecuritySupport.csrfTokenRepository(CSRF_COOKIE_NAME)));
        return http.build();
    }

    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        return new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    }
}
