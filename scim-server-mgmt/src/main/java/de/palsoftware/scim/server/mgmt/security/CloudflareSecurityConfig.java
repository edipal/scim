package de.palsoftware.scim.server.mgmt.security;

import de.palsoftware.scim.server.common.security.CloudflareJwtSecuritySupport;
import de.palsoftware.scim.server.common.security.MgmtSecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("cloudflare")
public class CloudflareSecurityConfig {

    private static final String CSRF_COOKIE_NAME = "SCIM_SERVER_MGMT_XSRF";

    private final String adminRole;
    private final String userRole;
    private final String roleClaim;
    private final String actuatorApiKey;

    public CloudflareSecurityConfig(@Value("${app.security.oidc.admin-role}") String adminRole,
                                    @Value("${app.security.oidc.user-role}") String userRole,
                                    @Value("${app.security.cloudflare.role-claim}") String roleClaim,
                                    @Value("${app.security.actuator.api-key}") String actuatorApiKey) {
        this.adminRole = adminRole;
        this.userRole = userRole;
        this.roleClaim = roleClaim;
        this.actuatorApiKey = actuatorApiKey;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtDecoder cloudflareJwtDecoder,
            Converter<Jwt, AbstractAuthenticationToken> cloudflareJwtAuthenticationConverter,
            BearerTokenResolver cloudflareBearerTokenResolver,
            @Value("${app.security.cloudflare.logout-url}") String logoutSuccessUrl) throws Exception {
        MgmtSecuritySupport.configureBaseSecurity(http, actuatorApiKey)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(cloudflareBearerTokenResolver)
                .jwt(jwt -> jwt
                    .decoder(cloudflareJwtDecoder)
                    .jwtAuthenticationConverter(cloudflareJwtAuthenticationConverter)))
            .logout(logout -> logout.logoutSuccessUrl(logoutSuccessUrl))
            .csrf(csrf -> csrf.csrfTokenRepository(MgmtSecuritySupport.csrfTokenRepository(CSRF_COOKIE_NAME)));
        return http.build();
    }

    @Bean
    public BearerTokenResolver cloudflareBearerTokenResolver(
            @Value("${app.security.cloudflare.token-header}") String tokenHeader) {
        return CloudflareJwtSecuritySupport.createBearerTokenResolver(tokenHeader);
    }

    @Bean
    public JwtDecoder cloudflareJwtDecoder(
            @Value("${app.security.cloudflare.issuer-uri}") String issuerUri,
            @Value("${app.security.cloudflare.audience}") String audience,
            @Value("${app.security.cloudflare.jwk-set-uri}") String jwkSetUri) {
        return CloudflareJwtSecuritySupport.createJwtDecoder(issuerUri, audience, jwkSetUri);
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> cloudflareJwtAuthenticationConverter() {
        return CloudflareJwtSecuritySupport.createJwtAuthenticationConverter(roleClaim, adminRole, userRole);
    }
}