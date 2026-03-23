package de.palsoftware.scim.server.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.palsoftware.scim.server.common.repository.WorkspaceTokenRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class BearerTokenAuthFilterTest {

    private WorkspaceTokenRepository tokenRepository;
    private BearerTokenAuthFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        tokenRepository = Mockito.mock(WorkspaceTokenRepository.class);
        filter = new BearerTokenAuthFilter(tokenRepository, new ObjectMapper());
        filterChain = Mockito.mock(FilterChain.class);
    }

    @Test
    void missingAuthorizationHeader_Returns401WithWwwAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/ws/" + UUID.randomUUID() + "/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void invalidBearerToken_Returns401WithWwwAuthenticate() throws Exception {
        when(tokenRepository.findByTokenHashAndNotRevoked(anyString())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/ws/" + UUID.randomUUID() + "/scim/v2/Users");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void emptyBearerToken_Returns401WithWwwAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/ws/" + UUID.randomUUID() + "/scim/v2/Users");
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void invalidWorkspaceId_Returns404_NoWwwAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/ws/not-a-uuid/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertNull(response.getHeader("WWW-Authenticate"));
    }

    @Test
    void nonScimPath_PassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void response401_ContainsScimErrorSchema() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/ws/" + UUID.randomUUID() + "/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("application/scim+json;charset=UTF-8", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("urn:ietf:params:scim:api:messages:2.0:Error"));
    }
}
