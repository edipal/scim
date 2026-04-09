package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MgmtSecuritySupportTest {

    @Test
    void addMappedAuthorities_alwaysGrantsUserRole() {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

        MgmtSecuritySupport.addMappedAuthorities(null, mappedAuthorities, "admin", "user");

        assertThat(mappedAuthorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void addMappedAuthorities_adminClaimAddsAdminAndKeepsDefaultUserRole() {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

        MgmtSecuritySupport.addMappedAuthorities(List.of("admin"), mappedAuthorities, "admin", "user");

        assertThat(mappedAuthorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }
}