package com.scimplayground.server.controller;

import com.scimplayground.server.model.ScimUser;
import com.scimplayground.server.scim.error.ScimException;
import com.scimplayground.server.scim.compat.CompatMode;
import com.scimplayground.server.scim.mapper.MsScimUserMapper;
import com.scimplayground.server.scim.mapper.ScimUserMapper;
import com.scimplayground.server.security.WorkspaceContext;
import com.scimplayground.server.service.ScimUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SCIM 2.0 Users endpoint per RFC 7644 §3.
 * Supports: POST (Create), GET (Read/List), PUT (Replace), PATCH (Modify), DELETE.
 */
@RestController
@RequestMapping({"/ws/{workspaceId}/scim/v2/Users", "/ws/{workspaceId}/scim/v2/{compat}/Users"})
@Transactional
public class ScimUserController {

    private static final MediaType SCIM_JSON = MediaType.parseMediaType("application/scim+json");

    private final ScimUserService userService;

    public ScimUserController(ScimUserService userService) {
        this.userService = userService;
    }

    /**
     * POST /Users — Create a new user (RFC 7644 §3.3)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(
            @PathVariable String workspaceId,
            @RequestBody Map<String, Object> body,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        String baseUrl = buildBaseUrl(request, workspaceId);
        String compatBaseUrl = buildBaseUrl(request, workspaceId, compat);

        ScimUser user = userService.createUser(wsId, body);
        List<Map<String, Object>> groups = userService.getUserGroups(user.getId(), baseUrl);
        CompatMode compatMode = CompatMode.fromString(compat);
        Map<String, Object> scimResponse = ScimUserMapper.toScimResponse(user, compatBaseUrl, groups);
        scimResponse = applyCompat(scimResponse, compatMode, false);

        return ResponseEntity.status(201)
                .contentType(SCIM_JSON)
                .header("Location", compatBaseUrl + "/Users/" + user.getId())
                .header("ETag", "W/\"" + user.getVersion() + "\"")
                .body(scimResponse);
    }

    /**
     * GET /Users/{id} — Read a specific user (RFC 7644 §3.4.1)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestParam(required = false) String attributes,
            @RequestParam(required = false) String excludedAttributes,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        UUID uid = parseUUID(userId, "User");
        String baseUrl = buildBaseUrl(request, workspaceId);
        String compatBaseUrl = buildBaseUrl(request, workspaceId, compat);

        ScimUser user = userService.getUser(wsId, uid);
        List<Map<String, Object>> groups = userService.getUserGroups(user.getId(), baseUrl);
        CompatMode compatMode = CompatMode.fromString(compat);
        Map<String, Object> scimResponse = ScimUserMapper.toScimResponse(user, compatBaseUrl, groups);
        scimResponse = applyCompat(scimResponse, compatMode, false);

        // Apply attribute projection
        applyAttributeProjection(scimResponse, attributes, excludedAttributes);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .header("ETag", "W/\"" + user.getVersion() + "\"")
                .body(scimResponse);
    }

    /**
     * GET /Users — List/filter users (RFC 7644 §3.4.2)
     */
    @GetMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> listUsers(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, defaultValue = "userName") String sortBy,
            @RequestParam(required = false, defaultValue = "ascending") String sortOrder,
            @RequestParam(required = false, defaultValue = "1") int startIndex,
            @RequestParam(required = false, defaultValue = "100") int count,
            @RequestParam(required = false) String attributes,
            @RequestParam(required = false) String excludedAttributes,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        String baseUrl = buildBaseUrl(request, workspaceId);
        String compatBaseUrl = buildBaseUrl(request, workspaceId, compat);

        if (startIndex < 1) startIndex = 1;
        if (count < 0) count = 0;
        if (count > 200) count = 200; // Enforce maxResults from ServiceProviderConfig

        Map<String, Object> result = userService.listUsers(wsId, filter, sortBy, sortOrder, startIndex, count);
        CompatMode compatMode = CompatMode.fromString(compat);

        // Convert ScimUser entities to SCIM response maps
        List<ScimUser> users = (List<ScimUser>) result.get("Resources");
        List<Map<String, Object>> resources = users.stream()
                .map(u -> {
                    List<Map<String, Object>> groups = userService.getUserGroups(u.getId(), baseUrl);
                    Map<String, Object> scimResp = ScimUserMapper.toScimResponse(u, compatBaseUrl, groups);
                    scimResp = applyCompat(scimResp, compatMode, true);
                    applyAttributeProjection(scimResp, attributes, excludedAttributes);
                    return scimResp;
                })
                .collect(Collectors.toList());
        result.put("Resources", resources);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .body(result);
    }

    /**
     * PUT /Users/{id} — Full replacement (RFC 7644 §3.5.1)
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> replaceUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        UUID uid = parseUUID(userId, "User");
        
        // Validate If-Match header for optimistic concurrency control (RFC 7644 §3.14)
        if (ifMatch != null) {
            ScimUser existing = userService.getUser(wsId, uid);
            String currentETag = "W/\"" + existing.getVersion() + "\"";
            if (!ifMatch.equals(currentETag)) {
                throw new ScimException(412, null, "Failed to update. Resource changed on the server.");
            }
        }
        
        String baseUrl = buildBaseUrl(request, workspaceId);
        String compatBaseUrl = buildBaseUrl(request, workspaceId, compat);
        ScimUser user = userService.replaceUser(wsId, uid, body);
        List<Map<String, Object>> groups = userService.getUserGroups(user.getId(), baseUrl);
        CompatMode compatMode = CompatMode.fromString(compat);
        Map<String, Object> scimResponse = ScimUserMapper.toScimResponse(user, compatBaseUrl, groups);
        scimResponse = applyCompat(scimResponse, compatMode, false);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .header("ETag", "W/\"" + user.getVersion() + "\"")
                .body(scimResponse);
    }

    /**
     * PATCH /Users/{id} — Partial modification (RFC 7644 §3.5.2)
     */
    @PatchMapping("/{userId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> patchUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        UUID uid = parseUUID(userId, "User");
        
        // Validate If-Match header for optimistic concurrency control (RFC 7644 §3.14)
        if (ifMatch != null) {
            ScimUser existing = userService.getUser(wsId, uid);
            String currentETag = "W/\"" + existing.getVersion() + "\"";
            if (!ifMatch.equals(currentETag)) {
                throw new ScimException(412, null, "Failed to update. Resource changed on the server.");
            }
        }
        
        String baseUrl = buildBaseUrl(request, workspaceId);
        String compatBaseUrl = buildBaseUrl(request, workspaceId, compat);

        // Validate PATCH schema
        List<String> schemas = (List<String>) body.get("schemas");
        if (schemas == null || !schemas.contains("urn:ietf:params:scim:api:messages:2.0:PatchOp")) {
            throw new ScimException(400, "invalidValue",
                    "PATCH request must include PatchOp schema");
        }

        List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException(400, "invalidValue", "PATCH Operations is required");
        }

        ScimUser user = userService.patchUser(wsId, uid, operations);
        List<Map<String, Object>> groups = userService.getUserGroups(user.getId(), baseUrl);
        CompatMode compatMode = CompatMode.fromString(compat);
        Map<String, Object> scimResponse = ScimUserMapper.toScimResponse(user, compatBaseUrl, groups);
        scimResponse = applyCompat(scimResponse, compatMode, false);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .header("ETag", "W/\"" + user.getVersion() + "\"")
                .body(scimResponse);
    }

    /**
     * DELETE /Users/{id} — Remove a user (RFC 7644 §3.6)
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @PathVariable(name = "compat", required = false) String compat) {

        UUID wsId = resolveWorkspaceId();
        UUID uid = parseUUID(userId, "User");
        userService.deleteUser(wsId, uid);

        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private UUID resolveWorkspaceId() {
        var ws = WorkspaceContext.getWorkspace();
        if (ws == null) {
            throw new ScimException(401, null, "Unauthorized");
        }
        return ws.getId();
    }

    private UUID parseUUID(String value, String resourceType) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ScimException(404, null, resourceType + " not found: " + value);
        }
    }

    private String buildBaseUrl(HttpServletRequest request, String workspaceId) {
        return buildBaseUrl(request, workspaceId, null);
    }

    private String buildBaseUrl(HttpServletRequest request, String workspaceId, String compat) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedPort = request.getHeader("X-Forwarded-Port");

        String scheme = forwardedProto != null ? forwardedProto.split(",")[0].trim() : request.getScheme();
        String host = forwardedHost != null ? forwardedHost.split(",")[0].trim() : request.getServerName();

        int port = request.getServerPort();
        if (forwardedPort != null) {
            try {
                port = Integer.parseInt(forwardedPort.split(",")[0].trim());
            } catch (NumberFormatException ignored) {
                // Fall back to server port when forwarded port is invalid.
            }
        }

        String portStr = (port == 80 || port == 443 || host.contains(":")) ? "" : ":" + port;
        String base = scheme + "://" + host + portStr + "/ws/" + workspaceId + "/scim/v2";
        if (compat != null && !compat.isBlank()) {
            return base + "/" + compat;
        }
        return base;
    }

    private Map<String, Object> applyCompat(Map<String, Object> scimResponse, CompatMode compatMode,
                                            boolean listResponse) {
        if (compatMode == CompatMode.MS) {
            return MsScimUserMapper.toMsCompat(scimResponse, listResponse);
        }
        return scimResponse;
    }


    /**
     * Apply SCIM attributes/excludedAttributes projection.
     * RFC 7644 §3.4.2.5 & §3.4.2.6
     */
    private void applyAttributeProjection(Map<String, Object> resource,
                                           String attributes, String excludedAttributes) {
        if (attributes != null && !attributes.isBlank()) {
            Set<String> requested = parseAttrList(attributes);
            // Always keep schemas, id, meta
            requested.add("schemas");
            requested.add("id");
            requested.add("meta");
            resource.keySet().retainAll(requested);
        } else if (excludedAttributes != null && !excludedAttributes.isBlank()) {
            Set<String> excluded = parseAttrList(excludedAttributes);
            // Never exclude schemas, id
            excluded.remove("schemas");
            excluded.remove("id");
            resource.keySet().removeAll(excluded);
        }
    }

    private Set<String> parseAttrList(String attrList) {
        Set<String> result = new LinkedHashSet<>();
        for (String attr : attrList.split(",")) {
            String trimmed = attr.trim();
            if (!trimmed.isEmpty()) {
                // Handle qualified names like urn:...User:userName → just use the last segment
                if (trimmed.contains(":")) {
                    // This is a fully qualified attribute
                    // Keep it as-is for extension URN matching, but also add the short name
                    result.add(trimmed);
                    // For enterprise extension URN, add the extension key
                    if (trimmed.startsWith("urn:")) {
                        result.add(trimmed);
                    }
                } else {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
