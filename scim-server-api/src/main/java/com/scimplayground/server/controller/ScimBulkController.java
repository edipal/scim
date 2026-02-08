package com.scimplayground.server.controller;

import com.scimplayground.server.model.ScimGroup;
import com.scimplayground.server.model.ScimUser;
import com.scimplayground.server.scim.error.ScimException;
import com.scimplayground.server.security.WorkspaceContext;
import com.scimplayground.server.service.ScimGroupService;
import com.scimplayground.server.service.ScimUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * SCIM 2.0 Bulk Operations endpoint per RFC 7644 §3.7.
 */
@RestController
@RequestMapping({"/ws/{workspaceId}/scim/v2/Bulk", "/ws/{workspaceId}/scim/v2/{compat}/Bulk"})
@Transactional
public class ScimBulkController {

    private static final MediaType SCIM_JSON = MediaType.parseMediaType("application/scim+json");
    private static final int MAX_OPERATIONS = 1000;

    private final ScimUserService userService;
    private final ScimGroupService groupService;

    public ScimBulkController(ScimUserService userService, ScimGroupService groupService) {
        this.userService = userService;
        this.groupService = groupService;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> processBulk(
            @PathVariable String workspaceId,
            @RequestBody Map<String, Object> body,
            @PathVariable(name = "compat", required = false) String compat,
            HttpServletRequest request) {

        UUID wsId = resolveWorkspaceId();
        String baseUrl = buildBaseUrl(request, workspaceId, compat);

        List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
        if (operations == null) {
            throw new ScimException(400, "invalidValue", "Bulk request must contain Operations");
        }

        // Check maxOperations
        if (operations.size() > MAX_OPERATIONS) {
            throw new ScimException(413, null,
                    "Bulk request exceeds maxOperations (" + MAX_OPERATIONS + ")");
        }

        // Process bulkId references — map bulkId → created resource ID
        Map<String, String> bulkIdMap = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> op : operations) {
            Map<String, Object> result = processOperation(op, wsId, baseUrl, bulkIdMap);
            results.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:BulkResponse"));
        response.put("Operations", results);

        return ResponseEntity.ok()
                .contentType(SCIM_JSON)
                .body(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processOperation(Map<String, Object> op, UUID wsId,
                                                   String baseUrl, Map<String, String> bulkIdMap) {
        String method = ((String) op.get("method")).toUpperCase();
        String path = (String) op.get("path");
        String bulkId = (String) op.get("bulkId");
        Map<String, Object> data = (Map<String, Object>) op.get("data");

        Map<String, Object> result = new LinkedHashMap<>();
        if (bulkId != null) result.put("bulkId", bulkId);
        result.put("method", method);

        try {
            // Resolve bulkId references in path
            if (path != null) {
                path = resolveBulkIdReferences(path, bulkIdMap);
            }

            // Resolve bulkId references in data
            if (data != null) {
                data = resolveBulkIdInData(data, bulkIdMap);
            }

            switch (method) {
                case "POST":
                    result.putAll(handlePost(path, data, wsId, baseUrl, bulkId, bulkIdMap));
                    break;
                case "PUT":
                    result.putAll(handlePut(path, data, wsId, baseUrl));
                    break;
                case "PATCH":
                    result.putAll(handlePatch(path, data, wsId, baseUrl));
                    break;
                case "DELETE":
                    result.putAll(handleDelete(path, wsId));
                    break;
                default:
                    result.put("status", "400");
                    result.put("response", buildError("400", "invalidValue", "Unsupported method: " + method));
            }
        } catch (ScimException e) {
            result.put("status", String.valueOf(e.getHttpStatus()));
            result.put("response", buildError(
                    String.valueOf(e.getHttpStatus()), e.getScimType(), e.getMessage()));
        } catch (Exception e) {
            result.put("status", "500");
            result.put("response", buildError("500", null, e.getMessage()));
        }

        return result;
    }

    private Map<String, Object> handlePost(String path, Map<String, Object> data, UUID wsId,
                                            String baseUrl, String bulkId, Map<String, String> bulkIdMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (path.startsWith("/Users")) {
            ScimUser user = userService.createUser(wsId, data);
            result.put("status", "201");
            result.put("location", baseUrl + "/Users/" + user.getId());
            if (bulkId != null) {
                bulkIdMap.put(bulkId, user.getId().toString());
            }
        } else if (path.startsWith("/Groups")) {
            ScimGroup group = groupService.createGroup(wsId, data);
            result.put("status", "201");
            result.put("location", baseUrl + "/Groups/" + group.getId());
            if (bulkId != null) {
                bulkIdMap.put(bulkId, group.getId().toString());
            }
        } else {
            throw new ScimException(400, "invalidValue", "Unknown resource path: " + path);
        }
        return result;
    }

    private Map<String, Object> handlePut(String path, Map<String, Object> data, UUID wsId, String baseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] parts = parsePath(path);
        String resourceType = parts[0];
        UUID resourceId = UUID.fromString(parts[1]);

        if ("Users".equals(resourceType)) {
            ScimUser user = userService.replaceUser(wsId, resourceId, data);
            result.put("status", "200");
            result.put("location", baseUrl + "/Users/" + user.getId());
        } else if ("Groups".equals(resourceType)) {
            ScimGroup group = groupService.replaceGroup(wsId, resourceId, data);
            result.put("status", "200");
            result.put("location", baseUrl + "/Groups/" + group.getId());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handlePatch(String path, Map<String, Object> data, UUID wsId, String baseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] parts = parsePath(path);
        String resourceType = parts[0];
        UUID resourceId = UUID.fromString(parts[1]);

        List<Map<String, Object>> operations = (List<Map<String, Object>>) data.get("Operations");
        if (operations == null) operations = List.of();

        if ("Users".equals(resourceType)) {
            ScimUser user = userService.patchUser(wsId, resourceId, operations);
            result.put("status", "200");
            result.put("location", baseUrl + "/Users/" + user.getId());
        } else if ("Groups".equals(resourceType)) {
            ScimGroup group = groupService.patchGroup(wsId, resourceId, operations);
            result.put("status", "200");
            result.put("location", baseUrl + "/Groups/" + group.getId());
        }
        return result;
    }

    private Map<String, Object> handleDelete(String path, UUID wsId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] parts = parsePath(path);
        String resourceType = parts[0];
        UUID resourceId = UUID.fromString(parts[1]);

        if ("Users".equals(resourceType)) {
            userService.deleteUser(wsId, resourceId);
        } else if ("Groups".equals(resourceType)) {
            groupService.deleteGroup(wsId, resourceId);
        }
        result.put("status", "204");
        return result;
    }

    private String[] parsePath(String path) {
        // path like /Users/uuid or /Groups/uuid
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = cleaned.split("/", 2);
        if (parts.length < 2) {
            throw new ScimException(400, "invalidPath", "Bulk path must include resource ID: " + path);
        }
        return parts;
    }

    private String resolveBulkIdReferences(String path, Map<String, String> bulkIdMap) {
        for (Map.Entry<String, String> entry : bulkIdMap.entrySet()) {
            path = path.replace("bulkId:" + entry.getKey(), entry.getValue());
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveBulkIdInData(Map<String, Object> data, Map<String, String> bulkIdMap) {
        Map<String, Object> resolved = new LinkedHashMap<>(data);
        for (Map.Entry<String, Object> entry : resolved.entrySet()) {
            if (entry.getValue() instanceof String) {
                String val = (String) entry.getValue();
                if (val.startsWith("bulkId:")) {
                    String ref = val.substring(7);
                    if (bulkIdMap.containsKey(ref)) {
                        entry.setValue(bulkIdMap.get(ref));
                    }
                }
            } else if (entry.getValue() instanceof Map) {
                entry.setValue(resolveBulkIdInData((Map<String, Object>) entry.getValue(), bulkIdMap));
            } else if (entry.getValue() instanceof List) {
                List<Object> list = (List<Object>) entry.getValue();
                List<Object> resolvedList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        resolvedList.add(resolveBulkIdInData((Map<String, Object>) item, bulkIdMap));
                    } else {
                        resolvedList.add(item);
                    }
                }
                entry.setValue(resolvedList);
            }
        }
        return resolved;
    }

    private Map<String, Object> buildError(String status, String scimType, String detail) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:Error"));
        error.put("status", status);
        if (scimType != null) error.put("scimType", scimType);
        if (detail != null) error.put("detail", detail);
        return error;
    }

    private UUID resolveWorkspaceId() {
        var ws = WorkspaceContext.getWorkspace();
        if (ws == null) throw new ScimException(401, null, "Unauthorized");
        return ws.getId();
    }

    private String buildBaseUrl(HttpServletRequest request, String workspaceId, String compat) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String portStr = (port == 80 || port == 443) ? "" : ":" + port;
        String base = scheme + "://" + host + portStr + "/ws/" + workspaceId + "/scim/v2";
        if (compat != null && !compat.isBlank()) {
            return base + "/" + compat;
        }
        return base;
    }
}
