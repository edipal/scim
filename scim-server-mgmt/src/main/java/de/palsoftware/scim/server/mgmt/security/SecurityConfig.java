package de.palsoftware.scim.server.mgmt.security;

import de.palsoftware.scim.server.mgmt.service.MgmtUserService;
import de.palsoftware.scim.server.common.security.ActuatorApiKeyAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String adminRole;
    private final String userRole;
    private final MgmtUserService mgmtUserService;
    private final String actuatorApiKey;

    public SecurityConfig(@Value("${app.security.oidc.admin-role:admin}") String adminRole,
                          @Value("${app.security.oidc.user-role:user}") String userRole,
                          @Lazy MgmtUserService mgmtUserService,
                          @Value("${ACTUATOR_API_KEY}") String actuatorApiKey) {
        this.adminRole = normalizeRole(adminRole);
        this.userRole = normalizeRole(userRole);
        this.mgmtUserService = mgmtUserService;
        this.actuatorApiKey = actuatorApiKey;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/error").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(actuatorApiKeyAuthFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/azure")
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService())))
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository()));
        return http.build();
    }

    @Bean
    public ActuatorApiKeyAuthFilter actuatorApiKeyAuthFilter() {
        return new ActuatorApiKeyAuthFilter(actuatorApiKey);
    }

    // CSRF token cookies intentionally omit HttpOnly so JavaScript can read and submit the token
    // (Double Submit Cookie pattern). The session cookie remains HttpOnly. The Secure flag is
    // set automatically by Spring Security based on request.isSecure(), so HTTPS-only in production.
    @SuppressWarnings("java:S3330")
    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("SCIM_SERVER_MGMT_XSRF");
        return repository;
    }

    private OidcUserService oidcUserService() {
        OidcUserService delegate = new OidcUserService();

        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
                OidcUser oidcUser = delegate.loadUser(userRequest);
                Set<GrantedAuthority> mappedAuthorities = new HashSet<>(oidcUser.getAuthorities());

                addMappedAuthorities(oidcUser.getClaimAsStringList("roles"), mappedAuthorities);

                String sub = oidcUser.getSubject();
                String email = resolveEmail(oidcUser);
                if (sub != null && !sub.isBlank()) {
                    mgmtUserService.provisionUser(sub, email);
                }

                return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
            }
        };
    }

    private void addMappedAuthorities(List<String> roles, Set<GrantedAuthority> mappedAuthorities) {
        if (roles == null) {
            return;
        }
        for (String role : roles) {
            String normalized = normalizeRole(role);
            if (normalized == null) {
                continue;
            }
            if (normalized.equals(adminRole)) {
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            if (normalized.equals(userRole)) {
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
        }
    }

    private static String resolveEmail(OidcUser u) {
        String email = u.getEmail();
        if (email != null && !email.isBlank()) return email;
        String upn = u.getClaimAsString("upn");
        if (upn != null && !upn.isBlank()) return upn;
        String unique = u.getClaimAsString("unique_name");
        if (unique != null && !unique.isBlank()) return unique;
        return u.getPreferredUsername();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }
        return normalized;
    }
}
