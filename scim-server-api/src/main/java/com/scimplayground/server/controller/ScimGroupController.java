package com.scimplayground.server.controller;

import com.scimplayground.server.model.ScimGroup;
import com.scimplayground.server.scim.error.ScimException;
import com.scimplayground.server.scim.mapper.ScimGroupMapper;
import com.scimplayground.server.security.WorkspaceContext;
import com.scimplayground.server.service.ScimGroupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SCIM 2.0 Groups endpoint per RFC 7644 §3.
 */
@RestController
@RequestMapping({"/ws/{workspaceId}/scim/v2/Groups", "/ws/{workspaceId}/scim/v2/{compat}/Groups"})
@Transactional
public class ScimGroupController {

    private static final MediaType SCIM_JSON = MediaType.parseMediaType("application/scim+json");

    private final ScimGroupService groupService;

    public ScimGroupController(ScimGroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createGroup(
            @PathVariable String workspaceId,
            @RequestBody Map<String, Object> body,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        String baseUrl = buildBaseUrl(request, workspaceId, compat);

        ScimGroup group = groupService.createGroup(wsId, body);
        Map<String, Object> scimResponse = ScimGroupMapper.toScimResponse(group, baseUrl);

        return ResponseEntity.status(201)
                .contentType(SCIM_JSON)
                .header("Location", baseUrl + "/Groups/" + group.getId())
                .header("ETag", "W/\"" + group.getVersion() + "\"")
                .body(scimResponse);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> getGroup(
            @PathVariable String workspaceId,
            @PathVariable String groupId,
            @RequestParam(required = false) String attributes,
            @RequestParam(required = false) String excludedAttributes,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        UUID gid = parseUUID(groupId, "Group");
        String baseUrl = buildBaseUrl(request, workspaceId, compat);

        ScimGroup group = groupService.getGroup(wsId, gid);
        Map<String, Object> scimResponse = ScimGroupMapper.toScimResponse(group, baseUrl);

        applyAttributeProjection(scimResponse, attributes, excludedAttributes);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .header("ETag", "W/\"" + group.getVersion() + "\"")
                .body(scimResponse);
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> listGroups(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, defaultValue = "displayName") String sortBy,
            @RequestParam(required = false, defaultValue = "ascending") String sortOrder,
            @RequestParam(required = false, defaultValue = "1") int startIndex,
            @RequestParam(required = false, defaultValue = "100") int count,
            @RequestParam(required = false) String attributes,
            @RequestParam(required = false) String excludedAttributes,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        String baseUrl = buildBaseUrl(request, workspaceId, compat);

        if (startIndex < 1) startIndex = 1;
        if (count < 0) count = 0;
        if (count > 200) count = 200; // Enforce maxResults from ServiceProviderConfig

        Map<String, Object> result = groupService.listGroups(wsId, filter, sortBy, sortOrder, startIndex, count);

        List<ScimGroup> groups = (List<ScimGroup>) result.get("Resources");
        List<Map<String, Object>> resources = groups.stream()
                .map(g -> {
                    Map<String, Object> scimResp = ScimGroupMapper.toScimResponse(g, baseUrl);
                    applyAttributeProjection(scimResp, attributes, excludedAttributes);
                    return scimResp;
                })
                .collect(Collectors.toList());
        result.put("Resources", resources);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .body(result);
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> replaceGroup(
            @PathVariable String workspaceId,
            @PathVariable String groupId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        UUID gid = parseUUID(groupId, "Group");
        
        // Validate If-Match header for optimistic concurrency control
        if (ifMatch != null) {
            ScimGroup existing = groupService.getGroup(wsId, gid);
            String currentETag = "W/\"" + existing.getVersion() + "\"";
            if (!ifMatch.equals(currentETag)) {
                throw new ScimException(412, null, "Failed to update. Resource changed on the server.");
            }
        }
        
        String baseUrl = buildBaseUrl(request, workspaceId, compat);

        ScimGroup group = groupService.replaceGroup(wsId, gid, body);
        Map<String, Object> scimResponse = ScimGroupMapper.toScimResponse(group, baseUrl);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .header("ETag", "W/\"" + group.getVersion() + "\"")
                .body(scimResponse);
    }

    @PatchMapping("/{groupId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> patchGroup(
            @PathVariable String workspaceId,
            @PathVariable String groupId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        UUID gid = parseUUID(groupId, "Group");
        
        // Validate If-Match header for optimistic concurrency control
        if (ifMatch != null) {
            ScimGroup existing = groupService.getGroup(wsId, gid);
            String currentETag = "W/\"" + existing.getVersion() + "\"";
            if (!ifMatch.equals(currentETag)) {
                throw new ScimException(412, null, "Failed to update. Resource changed on the server.");
            }
        }
        
        String baseUrl = buildBaseUrl(request, workspaceId, compat);

        List<String> schemas = (List<String>) body.get("schemas");
        if (schemas == null || !schemas.contains("urn:ietf:params:scim:api:messages:2.0:PatchOp")) {
            throw new ScimException(400, "invalidValue",
                    "PATCH request must include PatchOp schema");
        }

        List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException(400, "invalidValue", "PATCH Operations is required");
        }

        ScimGroup group = groupService.patchGroup(wsId, gid, operations);
        Map<String, Object> scimResponse = ScimGroupMapper.toScimResponse(group, baseUrl);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .header("ETag", "W/\"" + group.getVersion() + "\"")
                .body(scimResponse);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable String workspaceId,
            @PathVariable String groupId,
            @PathVariable(name = "compat", required = false) String compat) {

        UUID wsId = resolveWorkspaceId();
        UUID gid = parseUUID(groupId, "Group");
        groupService.deleteGroup(wsId, gid);

        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private UUID resolveWorkspaceId() {
        var ws = WorkspaceContext.getWorkspace();
        if (ws == null) throw new ScimException(401, null, "Unauthorized");
        return ws.getId();
    }

    private UUID parseUUID(String value, String resourceType) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ScimException(404, null, resourceType + " not found: " + value);
        }
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

    private void applyAttributeProjection(Map<String, Object> resource,
                                           String attributes, String excludedAttributes) {
        if (attributes != null && !attributes.isBlank()) {
            Set<String> requested = parseAttrList(attributes);
            requested.add("schemas");
            requested.add("id");
            requested.add("meta");
            resource.keySet().retainAll(requested);
        } else if (excludedAttributes != null && !excludedAttributes.isBlank()) {
            Set<String> excluded = parseAttrList(excludedAttributes);
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
                result.add(trimmed);
            }
        }
        return result;
    }
}
