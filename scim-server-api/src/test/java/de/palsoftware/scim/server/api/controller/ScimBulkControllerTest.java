package de.palsoftware.scim.server.api.controller;

import de.palsoftware.scim.server.common.model.ScimGroup;
import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.api.service.ScimGroupService;
import de.palsoftware.scim.server.api.service.ScimUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class ScimBulkControllerTest {

    private ScimUserService userService;
    private ScimGroupService groupService;
    private ScimBulkController controller;
    private final UUID workspaceId = UUID.randomUUID();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(ScimUserService.class);
        groupService = Mockito.mock(ScimGroupService.class);
        controller = new ScimBulkController(userService, groupService);

        request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setRequestURI("/ws/" + workspaceId + "/scim/v2/Bulk");
    }

    @Test
    void testProcessBulk_EmptyOps_ThrowsException() {
        Map<String, Object> body = new HashMap<>(); // missing Operations
        String workspaceIdValue = workspaceId.toString();
        Exception e = assertThrows(Exception.class,
            () -> controller.processBulk(workspaceIdValue, body, null, request));
        assertTrue(e.getMessage().contains("Bulk request must contain Operations"));
    }

    @Test
    void testProcessBulk_TooManyOps_ThrowsException() {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> ops = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            ops.add(Map.of());
        }
        body.put("Operations", ops);

        String workspaceIdValue = workspaceId.toString();
        Exception e = assertThrows(Exception.class,
                () -> controller.processBulk(workspaceIdValue, body, null, request));
        assertTrue(e.getMessage().contains("exceeds maxOperations"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessBulk_PostUserAndGroup() {
        ScimUser user = new ScimUser();
        user.setId(UUID.randomUUID());
        when(userService.createUser(eq(workspaceId), any())).thenReturn(user);

        ScimGroup group = new ScimGroup();
        group.setId(UUID.randomUUID());
        when(groupService.createGroup(eq(workspaceId), any())).thenReturn(group);

        Map<String, Object> op1 = new HashMap<>();
        op1.put("method", "POST");
        op1.put("path", "/Users");
        op1.put("bulkId", "qwerty");
        op1.put("data", Map.of("userName", "test"));

        Map<String, Object> op2 = new HashMap<>();
        op2.put("method", "POST");
        op2.put("path", "/Groups");
        op2.put("bulkId", "ytrewq");
        op2.put("data", Map.of("displayName", "group1", "members", List.of(Map.of("value", "bulkId:qwerty"))));

        Map<String, Object> body = Map.of("Operations", List.of(op1, op2));

        ResponseEntity<Map<String, Object>> response = controller.processBulk(workspaceId.toString(), body, null,
                request);

        assertEquals(200, response.getStatusCode().value());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("Operations");
        assertEquals(2, results.size());

        assertEquals("POST", results.get(0).get("method"));
        assertEquals("201", results.get(0).get("status"));
        assertTrue(((String) results.get(0).get("location")).contains(user.getId().toString()));
        assertEquals("qwerty", results.get(0).get("bulkId"));

        assertEquals("POST", results.get(1).get("method"));
        assertEquals("201", results.get(1).get("status"));
        assertTrue(((String) results.get(1).get("location")).contains(group.getId().toString()));
        assertEquals("ytrewq", results.get(1).get("bulkId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessBulk_PutPatchDelete() {
        UUID userId = UUID.randomUUID();
        ScimUser mockUser = new ScimUser();
        mockUser.setId(userId);
        when(userService.replaceUser(eq(workspaceId), eq(userId), any(), any())).thenReturn(mockUser);
        when(userService.patchUser(eq(workspaceId), eq(userId), any(), any())).thenReturn(mockUser);

        UUID groupId = UUID.randomUUID();
        ScimGroup mockGroup = new ScimGroup();
        mockGroup.setId(groupId);
        when(groupService.replaceGroup(eq(workspaceId), eq(groupId), any(), any())).thenReturn(mockGroup);
        when(groupService.patchGroup(eq(workspaceId), eq(groupId), any(), any())).thenReturn(mockGroup);

        Map<String, Object> opUserPut = Map.of("method", "PUT", "path", "/Users/" + userId, "data", Map.of());
        Map<String, Object> opGroupPut = Map.of("method", "PUT", "path", "/Groups/" + groupId, "data", Map.of());
        Map<String, Object> opUserPatch = Map.of("method", "PATCH", "path", "/Users/" + userId, "data", Map.of());
        Map<String, Object> opGroupPatch = Map.of("method", "PATCH", "path", "/Groups/" + groupId, "data", Map.of());
        Map<String, Object> opUserDelete = Map.of("method", "DELETE", "path", "/Users/" + userId);
        Map<String, Object> opGroupDelete = Map.of("method", "DELETE", "path", "/Groups/" + groupId);
        Map<String, Object> opInvalidMethod = Map.of("method", "INVALID", "path", "/Users/" + userId);

        Map<String, Object> body = Map.of("Operations", List.of(
                opUserPut, opGroupPut, opUserPatch, opGroupPatch, opUserDelete, opGroupDelete, opInvalidMethod));

        ResponseEntity<Map<String, Object>> response = controller.processBulk(workspaceId.toString(), body, null,
                request);

        assertEquals(200, response.getStatusCode().value());
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("Operations");
        assertEquals(7, results.size());

        assertEquals("200", results.get(0).get("status")); // User PUT
        assertEquals("200", results.get(1).get("status")); // Group PUT
        assertEquals("200", results.get(2).get("status")); // User PATCH
        assertEquals("200", results.get(3).get("status")); // Group PATCH
        assertEquals("204", results.get(4).get("status")); // User DELETE
        assertEquals("204", results.get(5).get("status")); // Group DELETE
        assertEquals("400", results.get(6).get("status")); // Invalid
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessBulk_Errors() {
        // Invalid POST path
        Map<String, Object> opPostInvalid = Map.of("method", "POST", "path", "/Invalid", "data", Map.of());
        // Invalid PUT path (missing ID)
        Map<String, Object> opPutInvalid = Map.of("method", "PUT", "path", "/Users", "data", Map.of());

        Map<String, Object> body = Map.of("Operations", List.of(opPostInvalid, opPutInvalid));
        ResponseEntity<Map<String, Object>> response = controller.processBulk(workspaceId.toString(), body, null,
                request);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("Operations");
        assertEquals("400", results.get(0).get("status")); // User POST invalid path
        assertEquals("400", results.get(1).get("status")); // User PUT missing ID
    }
}
