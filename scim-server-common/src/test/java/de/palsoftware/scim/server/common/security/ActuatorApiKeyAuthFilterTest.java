package de.palsoftware.scim.server.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ActuatorApiKeyAuthFilterTest {

    private static final String SECRET_KEY = "secret";
    private static final String HEALTH_PATH = "/actuator/health";

    @Test
    void testConstructorRequiresKey() {
        assertThrows(IllegalArgumentException.class, () -> new ActuatorApiKeyAuthFilter(null));
        assertThrows(IllegalArgumentException.class, () -> new ActuatorApiKeyAuthFilter("   "));
    }

    @Test
    void testShouldNotFilter() {
        ActuatorApiKeyAuthFilter filter = new ActuatorApiKeyAuthFilter(SECRET_KEY);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/Users");
        assertTrue(filter.shouldNotFilter(req));

        req.setRequestURI(HEALTH_PATH);
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void testValidApiKey() throws Exception {
        ActuatorApiKeyAuthFilter filter = new ActuatorApiKeyAuthFilter(SECRET_KEY);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        req.setRequestURI(HEALTH_PATH);
        req.addHeader("X-API-KEY", SECRET_KEY);

        filter.doFilterInternal(req, res, chain);
        assertEquals(200, res.getStatus());
    }

    @Test
    void testInvalidApiKey() throws Exception {
        ActuatorApiKeyAuthFilter filter = new ActuatorApiKeyAuthFilter(SECRET_KEY);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        req.setRequestURI(HEALTH_PATH);
        req.addHeader("X-API-KEY", "wrong");

        filter.doFilterInternal(req, res, chain);
        assertEquals(401, res.getStatus());
    }
}
