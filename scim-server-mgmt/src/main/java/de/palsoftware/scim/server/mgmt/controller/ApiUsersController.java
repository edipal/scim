package de.palsoftware.scim.server.mgmt.controller;

import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.mgmt.dto.UserUpsertRequest;
import de.palsoftware.scim.server.mgmt.security.AuthenticatedUser;
import de.palsoftware.scim.server.mgmt.service.ScimAdminService;
import de.palsoftware.scim.server.mgmt.utils.PagedResponseMapper;
import de.palsoftware.scim.server.mgmt.utils.UserResponseMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiUsersController {

    private static final String KEY_USER_NAME = "userName";

    private final ScimAdminService scimAdminService;

    public ApiUsersController(ScimAdminService scimAdminService) {
        this.scimAdminService = scimAdminService;
    }

    @GetMapping("/workspaces/{workspaceId}/users")
    public ResponseEntity<Map<String, Object>> listUsers(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            Authentication authentication) {
        String username = AuthenticatedUser.username(authentication);
        boolean admin = AuthenticatedUser.isAdmin(authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(1, page);
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(KEY_USER_NAME).ascending());
        Page<ScimUser> users = scimAdminService.listUsersPage(
                UUID.fromString(workspaceId),
                q,
                pageRequest,
                username,
                admin);
        return ResponseEntity.ok(PagedResponseMapper.pagedResponse(
                users,
                UserResponseMapper::userToMap,
                safePage,
                safeSize));
    }

    @GetMapping("/workspaces/{workspaceId}/users/lookup")
    public ResponseEntity<List<Map<String, Object>>> lookupUsers(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        String username = AuthenticatedUser.username(authentication);
        boolean admin = AuthenticatedUser.isAdmin(authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pageRequest = PageRequest.of(0, safeSize, Sort.by(KEY_USER_NAME).ascending());
        Page<ScimUser> users = scimAdminService.listUsersPage(
                UUID.fromString(workspaceId),
                q,
                pageRequest,
                username,
                admin);
        return ResponseEntity.ok(users.stream().map(UserResponseMapper::userLookupToMap).toList());
    }

    @DeleteMapping("/workspaces/{workspaceId}/users")
    public ResponseEntity<Void> clearUsers(@PathVariable String workspaceId,
            Authentication authentication) {
        String username = AuthenticatedUser.username(authentication);
        boolean admin = AuthenticatedUser.isAdmin(authentication);
        scimAdminService.deleteAllUsers(UUID.fromString(workspaceId), username, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/users")
    public ResponseEntity<Map<String, Object>> createUser(
            @PathVariable String workspaceId,
            @RequestBody UserUpsertRequest request,
            Authentication authentication) {
        String username = AuthenticatedUser.username(authentication);
        boolean admin = AuthenticatedUser.isAdmin(authentication);
        ScimUser user = scimAdminService.createUser(
                UUID.fromString(workspaceId),
                request,
                username,
                admin);
        return ResponseEntity.status(201).body(UserResponseMapper.userToMap(user));
    }

    @PutMapping("/workspaces/{workspaceId}/users/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestBody UserUpsertRequest request,
            Authentication authentication) {
        String username = AuthenticatedUser.username(authentication);
        boolean admin = AuthenticatedUser.isAdmin(authentication);
        ScimUser user = scimAdminService.updateUser(
                UUID.fromString(workspaceId),
                UUID.fromString(userId),
                request,
                username,
                admin);
        return ResponseEntity.ok(UserResponseMapper.userToMap(user));
    }

    @DeleteMapping("/workspaces/{workspaceId}/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            Authentication authentication) {
        String username = AuthenticatedUser.username(authentication);
        boolean admin = AuthenticatedUser.isAdmin(authentication);
        scimAdminService.deleteUser(
                UUID.fromString(workspaceId),
                UUID.fromString(userId),
                username,
                admin);
        return ResponseEntity.noContent().build();
    }
}