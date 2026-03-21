package de.palsoftware.scim.server.api.controller;

import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.api.service.ScimUserService;
import de.palsoftware.scim.server.api.scim.error.ScimException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class ScimUserControllerTest {

    private ScimUserService userService;
    private ScimUserController controller;
    private ScimUser mockUser;
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(ScimUserService.class);
        controller = new ScimUserController(userService);

        mockUser = new ScimUser();
        mockUser.setId(userId);
        mockUser.setUserName("test.user");
        mockUser.setVersion(1L);
        mockUser.setCreatedAt(Instant.now());
        mockUser.setLastModified(Instant.now());

        request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setRequestURI("/ws/" + workspaceId + "/scim/v2/Users");
    }

    @Test
    void testCreateUser() {
        when(userService.createUser(eq(workspaceId), any())).thenReturn(mockUser);
        when(userService.getUserGroups(any(), any())).thenReturn(Collections.emptyList());

        Map<String, Object> body = Map.of("userName", "test.user");
        ResponseEntity<Map<String, Object>> response = controller.createUser(workspaceId.toString(), body, null, request);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("test.user", response.getBody().get("userName"));
        
        // Test compat branch MS
        response = controller.createUser(workspaceId.toString(), body, "entra", request);
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void testGetUser() {
        when(userService.getUser(workspaceId, userId)).thenReturn(mockUser);
        when(userService.getUserGroups(any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, Object>> response = controller.getUser(workspaceId.toString(), userId.toString(), null, null, null, request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("test.user", response.getBody().get("userName"));
        
        // With projection
        response = controller.getUser(workspaceId.toString(), userId.toString(), "userName", null, null, request);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListUsers() {
        Map<String, Object> result = new HashMap<>();
        result.put("Resources", List.of(mockUser));
        
        when(userService.listUsers(eq(workspaceId), any(), any(), any(), anyInt(), anyInt())).thenReturn(result);
        when(userService.getUserGroupsBatch(any(), any())).thenReturn(Collections.emptyMap());

        ResponseEntity<Map<String, Object>> response = controller.listUsers(workspaceId.toString(), null, "userName", "ascending", -1, 300, null, null, null, request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        List<Map<String, Object>> resources = (List<Map<String, Object>>) response.getBody().get("Resources");
        assertEquals(1, resources.size());
        assertEquals("test.user", resources.get(0).get("userName"));
    }

    @Test
    void testReplaceUser() {
        when(userService.replaceUser(eq(workspaceId), eq(userId), any(), any())).thenReturn(mockUser);
        when(userService.getUserGroups(any(), any())).thenReturn(Collections.emptyList());

        Map<String, Object> body = Map.of("userName", "test.user");
        ResponseEntity<Map<String, Object>> response = controller.replaceUser(workspaceId.toString(), userId.toString(), body, null, null, request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("test.user", response.getBody().get("userName"));
    }

    @Test
    void testPatchUser() {
        when(userService.patchUser(eq(workspaceId), eq(userId), any(), any())).thenReturn(mockUser);
        when(userService.getUserGroups(any(), any())).thenReturn(Collections.emptyList());

        Map<String, Object> body = new HashMap<>();
        body.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        body.put("Operations", List.of(Map.of("op", "replace", "path", "userName", "value", "updated")));

        ResponseEntity<Map<String, Object>> response = controller.patchUser(workspaceId.toString(), userId.toString(), body, null, null, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("test.user", response.getBody().get("userName"));
    }

    @Test
    void testPatchUser_Exception() {
        String workspaceIdValue = workspaceId.toString();
        String userIdValue = userId.toString();

        Map<String, Object> bodyMissingSchema = new HashMap<>();
        assertThrows(ScimException.class, () -> controller.patchUser(workspaceIdValue, userIdValue, bodyMissingSchema, null, null, request));

        Map<String, Object> bodyMissingOps = new HashMap<>();
        bodyMissingOps.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        assertThrows(ScimException.class, () -> controller.patchUser(workspaceIdValue, userIdValue, bodyMissingOps, null, null, request));
    }

    @Test
    void testDeleteUser() {
        Mockito.doNothing().when(userService).deleteUser(workspaceId, userId);

        ResponseEntity<Void> response = controller.deleteUser(workspaceId.toString(), userId.toString(), null);

        assertEquals(204, response.getStatusCode().value());
    }
}
