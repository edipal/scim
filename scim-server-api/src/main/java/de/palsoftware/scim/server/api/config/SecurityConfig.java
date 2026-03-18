package de.palsoftware.scim.server.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.palsoftware.scim.server.common.repository.WorkspaceRepository;
import de.palsoftware.scim.server.common.repository.WorkspaceTokenRepository;
import de.palsoftware.scim.server.api.security.BearerTokenAuthFilter;
import de.palsoftware.scim.server.api.logging.RequestResponseLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final WorkspaceTokenRepository tokenRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;
    private final RequestResponseLoggingFilter loggingFilter;

    public SecurityConfig(WorkspaceTokenRepository tokenRepository,
                          WorkspaceRepository workspaceRepository,
                          ObjectMapper objectMapper,
                          RequestResponseLoggingFilter loggingFilter) {
        this.tokenRepository = tokenRepository;
        this.workspaceRepository = workspaceRepository;
        this.objectMapper = objectMapper;
        this.loggingFilter = loggingFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health").permitAll()
                .requestMatchers("/ws/*/scim/v2/**").permitAll() // Auth handled by our filter
                .anyRequest().permitAll()
            )
            .addFilterBefore(bearerTokenAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(loggingFilter, BearerTokenAuthFilter.class);

        return http.build();
    }

    @Bean
    public BearerTokenAuthFilter bearerTokenAuthFilter() {
        return new BearerTokenAuthFilter(tokenRepository, workspaceRepository, objectMapper);
    }
}
