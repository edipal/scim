package de.palsoftware.scim.server.mgmt.security;

import de.palsoftware.scim.server.common.security.AzureOidcSecuritySupport;
import de.palsoftware.scim.server.common.security.MgmtSecuritySupport;
import de.palsoftware.scim.server.mgmt.service.MgmtUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableWebSecurity
@Profile("!cloudflare")
public class AzureSecurityConfig {

    private static final String CSRF_COOKIE_NAME = "SCIM_SERVER_MGMT_XSRF";

    private final String adminRole;
    private final String userRole;
    private final String roleClaim;
    private final String actuatorApiKey;
    private final MgmtUserService mgmtUserService;

    public AzureSecurityConfig(@Value("${app.security.oidc.admin-role}") String adminRole,
                               @Value("${app.security.oidc.user-role}") String userRole,
                               @Value("${app.security.azure.role-claim}") String roleClaim,
                               @Lazy MgmtUserService mgmtUserService,
                               @Value("${app.security.actuator.api-key}") String actuatorApiKey) {
        this.adminRole = adminRole;
        this.userRole = userRole;
        this.roleClaim = roleClaim;
        this.actuatorApiKey = actuatorApiKey;
        this.mgmtUserService = mgmtUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        MgmtSecuritySupport.configureBaseSecurity(http, actuatorApiKey)
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/azure")
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(
                        AzureOidcSecuritySupport.createOidcUserService(
                                roleClaim,
                                adminRole,
                                userRole,
                                mgmtUserService::provisionUser))))
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .csrf(csrf -> csrf.csrfTokenRepository(MgmtSecuritySupport.csrfTokenRepository(CSRF_COOKIE_NAME)));
        return http.build();
    }
}