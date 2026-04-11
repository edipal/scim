package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenSecurityUtilTest {

    @Test
    void testGenerateWorkspaceTokenIsUrlSafeAndExpectedLength() {
        String token = TokenSecurityUtil.generateSecureToken();

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals(86, token.length());
        assertFalse(token.contains("="));
        assertTrue(token.matches("^[A-Za-z0-9_-]+$"));
    }

    @Test
    void testGenerateWorkspaceTokenProducesDistinctValues() {
        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 16; i++) {
            tokens.add(TokenSecurityUtil.generateSecureToken());
        }

        assertEquals(16, tokens.size());
    }

    @Test
    void testSha256Hex() {
        String hash = TokenSecurityUtil.sha256Hex("test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash);
    }
}
