package de.palsoftware.scim.server.api.controller;

import de.palsoftware.scim.server.api.scim.error.ScimException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

abstract class ScimBaseController {

    protected ScimBaseController() {
    }

    protected static final String KEY_SCHEMAS = "schemas";

    protected static UUID resolveWorkspaceId(String workspaceId) {
        try {
            return UUID.fromString(workspaceId);
        } catch (IllegalArgumentException e) {
            throw new ScimException(404, null, "Invalid workspace ID: " + workspaceId);
        }
    }

    protected static UUID parseUUID(String value, String resourceType) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ScimException(404, null, resourceType + " not found: " + value);
        }
    }

    protected static String buildBaseUrl(HttpServletRequest request, String workspaceId) {
        return buildBaseUrl(request, workspaceId, null);
    }

    protected static String buildBaseUrl(HttpServletRequest request, String workspaceId, String compat) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedPort = request.getHeader("X-Forwarded-Port");

        String scheme = forwardedProto != null ? sanitizeHeaderValue(forwardedProto.split(",")[0].trim()) : request.getScheme();
        String host = forwardedHost != null ? sanitizeHeaderValue(forwardedHost.split(",")[0].trim()) : request.getServerName();

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

    private static String sanitizeHeaderValue(String value) {
        // Reject header values containing newlines (header injection) or non-printable characters
        if (value == null) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new ScimException(400, "invalidValue", "Invalid header value");
            }
        }
        return value;
    }

    protected static void applyAttributeProjection(Map<String, Object> resource,
                                                    String attributes, String excludedAttributes) {
        if (attributes != null && !attributes.isBlank()) {
            Set<String> requested = parseAttrList(attributes);
            requested.add(KEY_SCHEMAS);
            requested.add("id");
            requested.add("meta");
            resource.keySet().retainAll(requested);
        } else if (excludedAttributes != null && !excludedAttributes.isBlank()) {
            Set<String> excluded = parseAttrList(excludedAttributes);
            excluded.remove(KEY_SCHEMAS);
            excluded.remove("id");
            resource.keySet().removeAll(excluded);
        }
    }

    protected static Set<String> parseAttrList(String attrList) {
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
