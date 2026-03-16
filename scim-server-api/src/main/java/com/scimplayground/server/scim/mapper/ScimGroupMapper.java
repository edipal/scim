package com.scimplayground.server.scim.mapper;

import com.scimplayground.server.model.ScimGroup;
import com.scimplayground.server.model.ScimGroupMembership;

import java.util.*;

/**
 * Converts between ScimGroup entity and SCIM JSON Map representation.
 */
public class ScimGroupMapper {

    private ScimGroupMapper() {
    }

    public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    public static Map<String, Object> toScimResponse(ScimGroup group, String baseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", List.of(GROUP_SCHEMA));
        result.put("id", group.getId().toString());

        if (group.getExternalId() != null) {
            result.put("externalId", group.getExternalId());
        }

        result.put("displayName", group.getDisplayName());

        // Members
        if (group.getMembers() != null && !group.getMembers().isEmpty()) {
            List<Map<String, Object>> members = group.getMembers().stream()
                    .map(m -> memberToMap(m, baseUrl))
                    .toList();
            result.put("members", members);
        }

        // Meta
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "Group");
        meta.put("created", group.getCreatedAt().toString());
        meta.put("lastModified", group.getLastModified().toString());
        meta.put("location", baseUrl + "/Groups/" + group.getId());
        meta.put("version", "W/\"" + group.getVersion() + "\"");
        result.put("meta", meta);

        return result;
    }

    private static Map<String, Object> memberToMap(ScimGroupMembership m, String baseUrl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", m.getMemberValue().toString());

        String type = m.getMemberType() != null ? m.getMemberType() : "User";
        String resourcePath = "User".equalsIgnoreCase(type) ? "/Users/" : "/Groups/";
        map.put("$ref", baseUrl + resourcePath + m.getMemberValue());
        if (m.getDisplay() != null) map.put("display", m.getDisplay());
        map.put("type", type);
        return map;
    }
}
