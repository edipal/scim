package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenSecurityUtilTest {
    @Test
    void testTokenGeneration() {
        String token = TokenSecurityUtil.generateUrlSafeToken(64);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testSha256Hex() {
        String hash = TokenSecurityUtil.sha256Hex("test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash);
    }
}
