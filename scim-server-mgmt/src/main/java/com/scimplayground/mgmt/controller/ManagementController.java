package com.scimplayground.mgmt.controller;

import com.scimplayground.mgmt.dto.GroupUpsertRequest;
import com.scimplayground.mgmt.dto.GenerateDataRequest;
import com.scimplayground.mgmt.dto.UserUpsertRequest;
import com.scimplayground.mgmt.model.MgmtUser;
import com.scimplayground.mgmt.repository.MgmtUserRepository;
import com.scimplayground.mgmt.security.AuthenticatedUser;
import com.scimplayground.mgmt.service.ScimAdminService;
import com.scimplayground.mgmt.service.WorkspaceDataGeneratorService;
import com.scimplayground.mgmt.service.WorkspaceService;
import com.scimplayground.server.model.ScimGroup;
import com.scimplayground.server.model.ScimRequestLog;
import com.scimplayground.server.model.ScimUser;
import com.scimplayground.server.model.Workspace;
import com.scimplayground.server.model.WorkspaceToken;
import com.scimplayground.server.repository.ScimRequestLogRepository;
import com.scimplayground.server.repository.WorkspaceDataStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Management API for workspaces and tokens.
 * Not part of SCIM spec — used by the playground UI and setup scripts.
 */
@RestController
@RequestMapping("/api/management")
public class ManagementController {

    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_EXTERNAL_ID = "externalId";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_VALUE = "value";
    private static final String KEY_TYPE = "type";
    private static final String KEY_DISPLAY = "display";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_LAST_MODIFIED = "lastModified";
    private static final String KEY_TOTAL = "total";

    private final WorkspaceService workspaceService;
    private final ScimAdminService scimAdminService;
    private final WorkspaceDataGeneratorService workspaceDataGeneratorService;
    private final ScimRequestLogRepository logRepository;
    private final MgmtUserRepository mgmtUserRepository;

    public ManagementController(WorkspaceService workspaceService,
                                ScimAdminService scimAdminService,
                                WorkspaceDataGeneratorService workspaceDataGeneratorService,
                                ScimRequestLogRepository logRepository,
                                MgmtUserRepository mgmtUserRepository) {
        this.workspaceService = workspaceService;
        this.scimAdminService = scimAdminService;
        this.workspaceDataGeneratorService = workspaceDataGeneratorService;
        this.logRepository = logRepository;
        this.mgmtUserRepository = mgmtUserRepository;
    }

    // ─── Workspaces ─────────────────────────────────────────────────────

    @PostMapping("/workspaces")
    public ResponseEntity<Map<String, Object>> createWorkspace(@RequestBody Map<String, String> body,
                                                               Authentication authentication) {
        String name = body.get(KEY_NAME);
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        String description = body.get(KEY_DESCRIPTION);
        Workspace ws = workspaceService.createWorkspace(name, description, actorUsername(authentication));
        return ResponseEntity.status(201).body(workspaceToMap(ws));
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<Map<String, Object>>> listWorkspaces(Authentication authentication) {
        List<Workspace> workspaces = workspaceService.listWorkspaces(actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(workspaces.stream()
                .map(this::workspaceToMap)
                .toList());
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<Map<String, Object>> getWorkspace(@PathVariable String workspaceId,
                                                            Authentication authentication) {
        UUID wsId = UUID.fromString(workspaceId);
        return workspaceService.getWorkspace(wsId, actorUsername(authentication), isAdmin(authentication))
            .map(ws -> ResponseEntity.ok(workspaceDetailToMap(
                ws,
                workspaceService.getWorkspaceDataStats(wsId, actorUsername(authentication), isAdmin(authentication)))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/workspaces/{workspaceId}/stats")
    public ResponseEntity<Map<String, Object>> getWorkspaceStats(@PathVariable String workspaceId,
                                                                 Authentication authentication) {
        UUID wsId = UUID.fromString(workspaceId);
        WorkspaceDataStats stats = workspaceService.getWorkspaceDataStats(wsId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(workspaceStatsToMap(stats));
    }

    @DeleteMapping("/workspaces/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable String workspaceId,
                                                Authentication authentication) {
        UUID wsId = UUID.fromString(workspaceId);
        workspaceService.deleteWorkspace(wsId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    // ─── Logs ───────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/logs")
    @Transactional(readOnly = true)
        public ResponseEntity<Map<String, Object>> listLogs(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(1, page);
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(KEY_CREATED_AT).descending());
        Page<ScimRequestLog> logs = logRepository.findByWorkspace_IdOrderByCreatedAtDesc(wsId, pageRequest);
        return ResponseEntity.ok(pagedResponse(logs.map(this::logToMap), safePage, safeSize));
    }

    @DeleteMapping("/workspaces/{workspaceId}/logs")
    @Transactional
    public ResponseEntity<Void> clearLogs(@PathVariable String workspaceId,
                                          Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        logRepository.deleteByWorkspaceId(wsId);
        return ResponseEntity.noContent().build();
    }

    // ─── Tokens ─────────────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/tokens")
    public ResponseEntity<Map<String, Object>> createToken(
            @PathVariable String workspaceId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID wsId = UUID.fromString(workspaceId);
        String name = body != null ? body.get(KEY_NAME) : null;
        String description = body != null ? body.get(KEY_DESCRIPTION) : null;
        String rawToken = workspaceService.generateToken(
            wsId,
            name,
            description,
            actorUsername(authentication),
            isAdmin(authentication));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", rawToken);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/workspaces/{workspaceId}/tokens")
    public ResponseEntity<List<Map<String, Object>>> listTokens(@PathVariable String workspaceId,
                                                                Authentication authentication) {
        UUID wsId = UUID.fromString(workspaceId);
        List<WorkspaceToken> tokens = workspaceService.listTokens(wsId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(tokens.stream()
                .map(this::tokenToMap)
                .toList());
    }

    @DeleteMapping("/workspaces/{workspaceId}/tokens/{tokenId}")
    public ResponseEntity<Void> revokeToken(
            @PathVariable String workspaceId,
            @PathVariable String tokenId,
            Authentication authentication) {
        workspaceService.revokeToken(
            UUID.fromString(workspaceId),
            UUID.fromString(tokenId),
            actorUsername(authentication),
            isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

        // ─── Sample Data ────────────────────────────────────────────────────

        @PostMapping("/workspaces/{workspaceId}/generate/{kind}")
        @Transactional
        public ResponseEntity<Map<String, Object>> generateData(
            @PathVariable String workspaceId,
            @PathVariable String kind,
            @RequestBody(required = false) GenerateDataRequest request,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        WorkspaceDataGeneratorService.GenerationSummary summary = switch (kind.toLowerCase()) {
            case "users" -> workspaceDataGeneratorService.generateUsers(
                wsId,
                request != null ? request.count() : null,
                actorUsername(authentication),
                isAdmin(authentication));
            case "groups" -> workspaceDataGeneratorService.generateGroups(
                wsId,
                request != null ? request.count() : null,
                actorUsername(authentication),
                isAdmin(authentication));
            case "relations" -> workspaceDataGeneratorService.generateRelations(
                wsId,
                request != null ? request.count() : null,
                actorUsername(authentication),
                isAdmin(authentication));
            case "all" -> workspaceDataGeneratorService.generateAll(
                wsId,
                request != null ? request.count() : null,
                actorUsername(authentication),
                isAdmin(authentication));
            default -> throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported generator kind: " + kind);
        };

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestedCount", summary.requestedCount());
        response.put("appliedCount", summary.appliedCount());
        response.put("usersCreated", summary.usersCreated());
        response.put("groupsCreated", summary.groupsCreated());
        response.put("relationsCreated", summary.relationsCreated());
        return ResponseEntity.ok(response);
        }

    // ─── Users ──────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/users")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> listUsers(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(1, page);
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(KEY_USER_NAME).ascending());
        Page<ScimUser> users = scimAdminService.listUsersPage(wsId, q, pageRequest, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(pagedResponse(users.map(this::userToMap), safePage, safeSize));
    }

    @GetMapping("/workspaces/{workspaceId}/users/lookup")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> lookupUsers(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pageRequest = PageRequest.of(0, safeSize, Sort.by(KEY_USER_NAME).ascending());
        Page<ScimUser> users = scimAdminService.listUsersPage(wsId, q, pageRequest, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(users.stream()
                .map(this::userLookupToMap)
                .toList());
    }

    @DeleteMapping("/workspaces/{workspaceId}/users")
    @Transactional
    public ResponseEntity<Void> clearUsers(@PathVariable String workspaceId,
                                           Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        scimAdminService.deleteAllUsers(wsId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/users")
    @Transactional
    public ResponseEntity<Map<String, Object>> createUser(
            @PathVariable String workspaceId,
            @RequestBody UserUpsertRequest request,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        ScimUser user = scimAdminService.createUser(wsId, request, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.status(201).body(userToMap(user));
    }

    @PutMapping("/workspaces/{workspaceId}/users/{userId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestBody UserUpsertRequest request,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        UUID uId = UUID.fromString(userId);
        ScimUser user = scimAdminService.updateUser(wsId, uId, request, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(userToMap(user));
    }

    @DeleteMapping("/workspaces/{workspaceId}/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        UUID uId = UUID.fromString(userId);
        scimAdminService.deleteUser(wsId, uId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    // ─── Groups ─────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/groups")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> listGroups(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(1, page);
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(KEY_DISPLAY_NAME).ascending());
        Page<ScimGroup> groups = scimAdminService.listGroupsPage(wsId, q, pageRequest, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(pagedResponse(groups.map(this::groupToMap), safePage, safeSize));
    }

    @GetMapping("/workspaces/{workspaceId}/groups/lookup")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> lookupGroups(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pageRequest = PageRequest.of(0, safeSize, Sort.by(KEY_DISPLAY_NAME).ascending());
        Page<ScimGroup> groups = scimAdminService.listGroupsPage(wsId, q, pageRequest, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(groups.stream()
                .map(this::groupLookupToMap)
                .toList());
    }

    @DeleteMapping("/workspaces/{workspaceId}/groups")
    @Transactional
    public ResponseEntity<Void> clearGroups(@PathVariable String workspaceId,
                                            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        scimAdminService.deleteAllGroups(wsId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/groups")
    @Transactional
    public ResponseEntity<Map<String, Object>> createGroup(
            @PathVariable String workspaceId,
            @RequestBody GroupUpsertRequest request,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        ScimGroup group = scimAdminService.createGroup(wsId, request, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.status(201).body(groupToMap(group));
    }

    @PutMapping("/workspaces/{workspaceId}/groups/{groupId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateGroup(
            @PathVariable String workspaceId,
            @PathVariable String groupId,
            @RequestBody GroupUpsertRequest request,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        UUID gId = UUID.fromString(groupId);
        ScimGroup group = scimAdminService.updateGroup(wsId, gId, request, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.ok(groupToMap(group));
    }

    @DeleteMapping("/workspaces/{workspaceId}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable String workspaceId,
            @PathVariable String groupId,
            Authentication authentication) {
        UUID wsId = resolveWorkspaceIdWithAccess(workspaceId, authentication);
        UUID gId = UUID.fromString(groupId);
        scimAdminService.deleteGroup(wsId, gId, actorUsername(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private Map<String, Object> workspaceToMap(Workspace ws) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", ws.getId().toString());
        map.put(KEY_NAME, ws.getName());
        map.put(KEY_DESCRIPTION, ws.getDescription());
        map.put("createdByUsername", ws.getCreatedByUsername());
        String ownerName = ws.getCreatedByUsername() != null
                ? mgmtUserRepository.findById(ws.getCreatedByUsername())
                        .map(MgmtUser::getEmail)
                        .orElse(null)
                : null;
        map.put("createdByDisplayName", ownerName);
        map.put(KEY_CREATED_AT, ws.getCreatedAt() != null ? ws.getCreatedAt().toString() : null);
        map.put("updatedAt", ws.getUpdatedAt() != null ? ws.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> workspaceDetailToMap(Workspace ws, WorkspaceDataStats stats) {
        Map<String, Object> map = workspaceToMap(ws);
        map.put("stats", workspaceStatsToMap(stats));
        return map;
    }

    private Map<String, Object> workspaceStatsToMap(WorkspaceDataStats stats) {
        Map<String, Object> map = new LinkedHashMap<>();

        Map<String, Object> objects = new LinkedHashMap<>();
        objects.put(KEY_TOTAL, stats.objectCount());
        objects.put("users", stats.userCount());
        objects.put("groups", stats.groupCount());
        objects.put("tokens", stats.tokenCount());
        objects.put("logs", stats.logCount());
        objects.put("userAttributeRows", stats.userAttributeObjectCount());

        Map<String, Object> userAttributes = new LinkedHashMap<>();
        userAttributes.put("emails", stats.emailCount());
        userAttributes.put("phoneNumbers", stats.phoneNumberCount());
        userAttributes.put("addresses", stats.addressCount());
        userAttributes.put("entitlements", stats.entitlementCount());
        userAttributes.put("roles", stats.roleCount());
        userAttributes.put("ims", stats.imCount());
        userAttributes.put("photos", stats.photoCount());
        userAttributes.put("x509Certificates", stats.x509CertificateCount());
        objects.put("userAttributes", userAttributes);

        Map<String, Object> relations = new LinkedHashMap<>();
        relations.put(KEY_TOTAL, stats.relationCount());
        relations.put("groupMemberships", stats.groupMembershipCount());

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("estimatedRowBytes", stats.estimatedRowBytes());
        storage.put("storedRows", stats.storedRowCount());

        map.put("objects", objects);
        map.put("relations", relations);
        map.put("storage", storage);
        return map;
    }

    private UUID resolveWorkspaceIdWithAccess(String workspaceId, Authentication authentication) {
        UUID wsId = UUID.fromString(workspaceId);
        workspaceService.requireWorkspaceAccess(wsId, actorUsername(authentication), isAdmin(authentication));
        return wsId;
    }

    private String actorUsername(Authentication authentication) {
        return AuthenticatedUser.username(authentication);
    }

    private boolean isAdmin(Authentication authentication) {
        return AuthenticatedUser.isAdmin(authentication);
    }

    private Map<String, Object> tokenToMap(WorkspaceToken token) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", token.getId().toString());
        map.put(KEY_NAME, token.getName());
        map.put(KEY_DESCRIPTION, token.getDescription());
        map.put("revoked", token.isRevoked());
        map.put(KEY_CREATED_AT, token.getCreatedAt() != null ? token.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> userToMap(ScimUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put(KEY_USER_NAME, user.getUserName());
        map.put(KEY_DISPLAY_NAME, user.getDisplayName());
        map.put(KEY_EXTERNAL_ID, user.getExternalId());
        map.put(KEY_NAME, userNameToMap(user));
        map.put("nickName", user.getNickName());
        map.put("profileUrl", user.getProfileUrl());
        map.put("title", user.getTitle());
        map.put("userType", user.getUserType());
        map.put("preferredLanguage", user.getPreferredLanguage());
        map.put("locale", user.getLocale());
        map.put("timezone", user.getTimezone());
        map.put("enterprise", enterpriseToMap(user));
        map.put("emails", user.getEmails().stream()
            .map(email -> multiValueToMap(email.getValue(), email.getType(), email.getDisplay(), email.isPrimaryFlag()))
            .toList());
        map.put("phoneNumbers", user.getPhoneNumbers().stream()
            .map(phone -> multiValueToMap(phone.getValue(), phone.getType(), phone.getDisplay(), phone.isPrimaryFlag()))
            .toList());
        map.put("addresses", user.getAddresses().stream()
            .map(this::addressToMap)
            .toList());
        map.put("entitlements", user.getEntitlements().stream()
            .map(entitlement -> multiValueToMap(entitlement.getValue(), entitlement.getType(), entitlement.getDisplay(), entitlement.isPrimaryFlag()))
            .toList());
        map.put("roles", user.getRoles().stream()
            .map(role -> multiValueToMap(role.getValue(), role.getType(), role.getDisplay(), role.isPrimaryFlag()))
            .toList());
        map.put("ims", user.getIms().stream()
            .map(im -> multiValueToMap(im.getValue(), im.getType(), im.getDisplay(), im.isPrimaryFlag()))
            .toList());
        map.put("photos", user.getPhotos().stream()
            .map(photo -> multiValueToMap(photo.getValue(), photo.getType(), photo.getDisplay(), photo.isPrimaryFlag()))
            .toList());
        map.put("x509Certificates", user.getX509Certificates().stream()
            .map(cert -> multiValueToMap(cert.getValue(), cert.getType(), cert.getDisplay(), cert.isPrimaryFlag()))
            .toList());
        map.put("active", user.isActive());
        map.put(KEY_CREATED_AT, user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put(KEY_LAST_MODIFIED, user.getLastModified() != null ? user.getLastModified().toString() : null);
        map.put("meta", metaToMap(user.getCreatedAt(), user.getLastModified(), user.getVersion()));
        return map;
    }

    private Map<String, Object> userLookupToMap(ScimUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put(KEY_USER_NAME, user.getUserName());
        map.put(KEY_DISPLAY_NAME, user.getDisplayName());
        return map;
    }

    private Map<String, Object> groupToMap(ScimGroup group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId().toString());
        map.put(KEY_DISPLAY_NAME, group.getDisplayName());
        map.put(KEY_EXTERNAL_ID, group.getExternalId());
        map.put("members", group.getMembers().stream()
            .map(member -> {
                Map<String, Object> memberMap = new LinkedHashMap<>();
                memberMap.put(KEY_VALUE, member.getMemberValue() != null ? member.getMemberValue().toString() : null);
                memberMap.put(KEY_TYPE, member.getMemberType());
                memberMap.put(KEY_DISPLAY, member.getDisplay());
                return memberMap;
            })
            .toList());
        map.put(KEY_CREATED_AT, group.getCreatedAt() != null ? group.getCreatedAt().toString() : null);
        map.put(KEY_LAST_MODIFIED, group.getLastModified() != null ? group.getLastModified().toString() : null);
        map.put("meta", metaToMap(group.getCreatedAt(), group.getLastModified(), group.getVersion()));
        return map;
    }

    private Map<String, Object> groupLookupToMap(ScimGroup group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId().toString());
        map.put(KEY_DISPLAY_NAME, group.getDisplayName());
        map.put(KEY_EXTERNAL_ID, group.getExternalId());
        return map;
    }

    private Map<String, Object> logToMap(ScimRequestLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId() != null ? log.getId().toString() : null);
        map.put("workspaceId", log.getWorkspace() != null && log.getWorkspace().getId() != null
            ? log.getWorkspace().getId().toString()
            : null);
        map.put("method", log.getMethod());
        map.put("path", log.getPath());
        map.put("status", log.getStatus());
        map.put("requestBody", log.getRequestBody());
        map.put("responseBody", log.getResponseBody());
        map.put(KEY_CREATED_AT, log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> pagedResponse(Page<Map<String, Object>> page, int pageNumber, int size) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", page.getContent());
        map.put("page", pageNumber);
        map.put("size", size);
        map.put(KEY_TOTAL, page.getTotalElements());
        map.put("totalPages", page.getTotalPages());
        return map;
    }

    private Map<String, Object> userNameToMap(ScimUser user) {
        if (user.getNameFormatted() == null
                && user.getNameFamilyName() == null
                && user.getNameGivenName() == null
                && user.getNameMiddleName() == null
                && user.getNameHonorificPrefix() == null
                && user.getNameHonorificSuffix() == null) {
            return Map.of();
        }
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("formatted", user.getNameFormatted());
        name.put("familyName", user.getNameFamilyName());
        name.put("givenName", user.getNameGivenName());
        name.put("middleName", user.getNameMiddleName());
        name.put("honorificPrefix", user.getNameHonorificPrefix());
        name.put("honorificSuffix", user.getNameHonorificSuffix());
        return name;
    }

    private Map<String, Object> enterpriseToMap(ScimUser user) {
        if (user.getEnterpriseEmployeeNumber() == null
                && user.getEnterpriseCostCenter() == null
                && user.getEnterpriseOrganization() == null
                && user.getEnterpriseDivision() == null
                && user.getEnterpriseDepartment() == null
                && user.getEnterpriseManagerValue() == null
                && user.getEnterpriseManagerRef() == null
                && user.getEnterpriseManagerDisplay() == null) {
            return Map.of();
        }
        Map<String, Object> enterprise = new LinkedHashMap<>();
        enterprise.put("employeeNumber", user.getEnterpriseEmployeeNumber());
        enterprise.put("costCenter", user.getEnterpriseCostCenter());
        enterprise.put("organization", user.getEnterpriseOrganization());
        enterprise.put("division", user.getEnterpriseDivision());
        enterprise.put("department", user.getEnterpriseDepartment());
        Map<String, Object> manager = new LinkedHashMap<>();
        manager.put(KEY_VALUE, user.getEnterpriseManagerValue());
        manager.put("ref", user.getEnterpriseManagerRef());
        manager.put(KEY_DISPLAY, user.getEnterpriseManagerDisplay());
        enterprise.put("manager", manager);
        return enterprise;
    }

    private Map<String, Object> addressToMap(com.scimplayground.server.model.ScimUserAddress address) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("formatted", address.getFormatted());
        map.put("streetAddress", address.getStreetAddress());
        map.put("locality", address.getLocality());
        map.put("region", address.getRegion());
        map.put("postalCode", address.getPostalCode());
        map.put("country", address.getCountry());
        map.put(KEY_TYPE, address.getType());
        map.put("primary", address.isPrimaryFlag());
        return map;
    }

    private Map<String, Object> multiValueToMap(String value, String type, String display, boolean primary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_VALUE, value);
        map.put(KEY_TYPE, type);
        map.put(KEY_DISPLAY, display);
        map.put("primary", primary);
        return map;
    }

    private Map<String, Object> metaToMap(java.time.Instant createdAt, java.time.Instant lastModified, Long version) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(KEY_CREATED_AT, createdAt != null ? createdAt.toString() : null);
        meta.put(KEY_LAST_MODIFIED, lastModified != null ? lastModified.toString() : null);
        meta.put("version", version);
        return meta;
    }
}
