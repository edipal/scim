package de.palsoftware.scim.server.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CloudflareJwtSecuritySupport {

    private CloudflareJwtSecuritySupport() {
    }

    public static BearerTokenResolver createBearerTokenResolver(String tokenHeader) {
        return request -> {
            String token = request.getHeader(tokenHeader);
            if (token == null || token.isBlank()) {
                return null;
            }
            return token;
        };
    }

    public static JwtDecoder createJwtDecoder(String issuerUri, String audience, String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(resolveJwkSetUri(issuerUri, jwkSetUri)).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                jwt -> validateAudience(jwt, audience));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    public static Converter<Jwt, AbstractAuthenticationToken> createJwtAuthenticationConverter(String roleClaim,
                                                                                               String adminRole,
                                                                                               String userRole) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            Object roleClaimValue = resolveRoleClaimValue(jwt, roleClaim);
            MgmtSecuritySupport.addMappedAuthorities(
                    MgmtSecuritySupport.extractClaimValues(roleClaimValue),
                    mappedAuthorities,
                    adminRole,
                    userRole);
            return mappedAuthorities;
        });
        return converter;
    }

    private static Object resolveRoleClaimValue(Jwt jwt, String roleClaim) {
        Object directClaimValue = jwt.getClaim(roleClaim);
        if (directClaimValue != null) {
            return directClaimValue;
        }
        Object customClaim = jwt.getClaim("custom");
        if (customClaim instanceof Map<?, ?> customClaims) {
            return customClaims.get(roleClaim);
        }
        return null;
    }

    private static OAuth2TokenValidatorResult validateAudience(Jwt jwt, String audience) {
        if (jwt.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The required audience is missing", null));
    }

    private static String resolveJwkSetUri(String issuerUri, String jwkSetUri) {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return jwkSetUri;
        }
        if (issuerUri.endsWith("/")) {
            return issuerUri + "cdn-cgi/access/certs";
        }
        return issuerUri + "/cdn-cgi/access/certs";
    }
}