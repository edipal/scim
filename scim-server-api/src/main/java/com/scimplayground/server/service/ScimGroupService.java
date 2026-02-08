package com.scimplayground.server.service;

import com.scimplayground.server.model.*;
import com.scimplayground.server.repository.*;
import com.scimplayground.server.scim.error.ScimException;
import com.scimplayground.server.scim.filter.ScimFilterParser;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ScimGroupService {

    private final ScimGroupRepository groupRepository;
    private final ScimGroupMembershipRepository membershipRepository;
    private final ScimUserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    public ScimGroupService(ScimGroupRepository groupRepository,
            ScimGroupMembershipRepository membershipRepository,
            ScimUserRepository userRepository,
            WorkspaceRepository workspaceRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @SuppressWarnings("unchecked")
    public ScimGroup createGroup(UUID workspaceId, Map<String, Object> input) {
        String displayName = (String) input.get("displayName");
        if (displayName == null || displayName.isBlank()) {
            throw new ScimException(400, "invalidValue", "displayName is required");
        }

        if (groupRepository.findByDisplayNameAndWorkspaceId(displayName, workspaceId).isPresent()) {
            throw new ScimException(409, "uniqueness", "Group with displayName '" + displayName + "' already exists");
        }

        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ScimException(404, null, "Workspace not found"));

        ScimGroup group = new ScimGroup();
        group.setWorkspace(ws);
        group.setDisplayName(displayName);

        Object externalId = input.get("externalId");
        if (externalId != null) {
            group.setExternalId(externalId.toString());
        }

        group = groupRepository.save(group);

        // Add members
        List<Map<String, Object>> members = (List<Map<String, Object>>) input.get("members");
        if (members != null) {
            for (Map<String, Object> m : members) {
                addMember(group, workspaceId, m);
            }
            group = groupRepository.save(group);
        }

        return group;
    }

    public ScimGroup getGroup(UUID workspaceId, UUID groupId) {
        return groupRepository.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new ScimException(404, null, "Group not found: " + groupId));
    }

    public Map<String, Object> listGroups(UUID workspaceId, String filter, String sortBy,
            String sortOrder, int startIndex, int count) {
        Specification<ScimGroup> spec = ScimFilterParser.parseGroupFilter(filter, workspaceId);

        long totalResults = groupRepository.count(spec);

        if (count == 0) {
            return buildListResponse(Collections.emptyList(), totalResults, startIndex, 0);
        }

        String sortAttr = ScimFilterParser.resolveGroupSortAttribute(sortBy);
        Sort.Direction direction = "descending".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortAttr);

        List<ScimGroup> allMatching = groupRepository.findAll(spec, sort);
        int offset = Math.max(0, startIndex - 1);
        List<ScimGroup> page;
        if (offset >= allMatching.size()) {
            page = Collections.emptyList();
        } else {
            int end = Math.min(offset + count, allMatching.size());
            page = allMatching.subList(offset, end);
        }

        return buildListResponse(page, totalResults, startIndex, page.size());
    }

    private Map<String, Object> buildListResponse(List<ScimGroup> groups, long totalResults,
            int startIndex, int itemsPerPage) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        response.put("totalResults", (int) totalResults);
        response.put("startIndex", startIndex);
        response.put("itemsPerPage", itemsPerPage);
        response.put("Resources", groups);
        return response;
    }

    @SuppressWarnings("unchecked")
    public ScimGroup replaceGroup(UUID workspaceId, UUID groupId, Map<String, Object> input) {
        ScimGroup existing = getGroup(workspaceId, groupId);

        String displayName = (String) input.get("displayName");
        if (displayName == null || displayName.isBlank()) {
            throw new ScimException(400, "invalidValue", "displayName is required");
        }

        // Check uniqueness if changed
        if (!existing.getDisplayName().equals(displayName)) {
            if (groupRepository.findByDisplayNameAndWorkspaceId(displayName, workspaceId).isPresent()) {
                throw new ScimException(409, "uniqueness",
                        "Group with displayName '" + displayName + "' already exists");
            }
        }

        existing.setDisplayName(displayName);
        Object externalId = input.get("externalId");
        existing.setExternalId(externalId != null ? externalId.toString() : null);

        // Replace members entirely
        existing.getMembers().clear();
        groupRepository.saveAndFlush(existing);

        List<Map<String, Object>> members = (List<Map<String, Object>>) input.get("members");
        if (members != null) {
            for (Map<String, Object> m : members) {
                addMember(existing, workspaceId, m);
            }
        }

        return groupRepository.save(existing);
    }

    @SuppressWarnings("unchecked")
    public ScimGroup patchGroup(UUID workspaceId, UUID groupId, List<Map<String, Object>> operations) {
        ScimGroup group = getGroup(workspaceId, groupId);

        for (Map<String, Object> op : operations) {
            String opType = ((String) op.get("op")).toLowerCase();
            String path = (String) op.get("path");
            Object value = op.get("value");

            switch (opType) {
                case "add":
                    if ("members".equals(path) || path == null) {
                        List<Map<String, Object>> membersToAdd;
                        if (value instanceof List) {
                            membersToAdd = (List<Map<String, Object>>) value;
                        } else if (value instanceof Map) {
                            // If value is the entire body with members
                            Map<String, Object> valueMap = (Map<String, Object>) value;
                            if (valueMap.containsKey("members")) {
                                membersToAdd = (List<Map<String, Object>>) valueMap.get("members");
                            } else {
                                membersToAdd = List.of(valueMap);
                            }
                        } else {
                            throw new ScimException(400, "invalidValue", "Invalid value for members add");
                        }
                        for (Map<String, Object> m : membersToAdd) {
                            String memberValue = m.get("value") != null ? m.get("value").toString() : null;
                            if (memberValue == null)
                                continue;
                            // Check if already a member
                            boolean alreadyMember = group.getMembers().stream()
                                    .anyMatch(existing -> existing.getMemberValue().toString().equals(memberValue));
                            if (!alreadyMember) {
                                addMember(group, workspaceId, m);
                            }
                        }
                    } else if ("displayName".equals(path)) {
                        if (value instanceof String) {
                            group.setDisplayName((String) value);
                        }
                    }
                    break;

                case "replace":
                    if ("members".equals(path)) {
                        group.getMembers().clear();
                        if (value instanceof List) {
                            List<Map<String, Object>> newMembers = (List<Map<String, Object>>) value;
                            for (Map<String, Object> m : newMembers) {
                                addMember(group, workspaceId, m);
                            }
                        }
                    } else if ("displayName".equals(path)) {
                        if (value instanceof String) {
                            String newName = (String) value;
                            if (!group.getDisplayName().equals(newName)) {
                                if (groupRepository.findByDisplayNameAndWorkspaceId(newName, workspaceId).isPresent()) {
                                    throw new ScimException(409, "uniqueness",
                                            "Group with displayName '" + newName + "' already exists");
                                }
                            }
                            group.setDisplayName(newName);
                        }
                    } else if ("externalId".equals(path)) {
                        group.setExternalId(value != null ? value.toString() : null);
                    } else if (path == null && value instanceof Map) {
                        // Replace attributes in body
                        Map<String, Object> valueMap = (Map<String, Object>) value;
                        if (valueMap.containsKey("displayName")) {
                            group.setDisplayName((String) valueMap.get("displayName"));
                        }
                        if (valueMap.containsKey("externalId")) {
                            Object ext = valueMap.get("externalId");
                            group.setExternalId(ext != null ? ext.toString() : null);
                        }
                        if (valueMap.containsKey("members")) {
                            group.getMembers().clear();
                            List<Map<String, Object>> newMembers = (List<Map<String, Object>>) valueMap.get("members");
                            for (Map<String, Object> m : newMembers) {
                                addMember(group, workspaceId, m);
                            }
                        }
                    }
                    break;

                case "remove":
                    if ("members".equals(path)) {
                        // Remove all members
                        group.getMembers().clear();
                    } else if (path != null && path.startsWith("members[")) {
                        // Filtered remove, e.g., members[value eq "uuid"]
                        String filterExpr = path.substring(8, path.length() - 1);
                        String targetValue = extractFilterValue(filterExpr);
                        if (targetValue != null) {
                            group.getMembers().removeIf(m -> m.getMemberValue().toString().equals(targetValue));
                        }
                    } else if (value instanceof List) {
                        // Remove specific members by value
                        List<Map<String, Object>> membersToRemove = (List<Map<String, Object>>) value;
                        for (Map<String, Object> m : membersToRemove) {
                            String memberVal = m.get("value") != null ? m.get("value").toString() : null;
                            if (memberVal != null) {
                                group.getMembers()
                                        .removeIf(existing -> existing.getMemberValue().toString().equals(memberVal));
                            }
                        }
                    }
                    break;

                default:
                    throw new ScimException(400, "invalidValue", "Unsupported PATCH op: " + opType);
            }
        }

        return groupRepository.save(group);
    }

    public void deleteGroup(UUID workspaceId, UUID groupId) {
        ScimGroup group = getGroup(workspaceId, groupId);
        membershipRepository.deleteByMemberValue(groupId);
        groupRepository.delete(group);
    }

    private void addMember(ScimGroup group, UUID workspaceId, Map<String, Object> memberMap) {
        String memberValue = memberMap.get("value") != null ? memberMap.get("value").toString() : null;
        if (memberValue == null) {
            throw new ScimException(400, "invalidValue", "Member value is required");
        }

        UUID memberId;
        try {
            memberId = UUID.fromString(memberValue);
        } catch (IllegalArgumentException e) {
            throw new ScimException(400, "invalidValue", "Invalid member value (must be UUID): " + memberValue);
        }

        // Determine member type
        String memberType = memberMap.get("type") != null ? memberMap.get("type").toString() : "User";
        if (!"User".equalsIgnoreCase(memberType) && !"Group".equalsIgnoreCase(memberType)) {
            throw new ScimException(400, "invalidValue", "Invalid member type: " + memberType);
        }
        memberType = "Group".equalsIgnoreCase(memberType) ? "Group" : "User";

        // Verify the member exists
        if ("User".equalsIgnoreCase(memberType)) {
            userRepository.findByIdAndWorkspaceId(memberId, workspaceId)
                    .orElseThrow(() -> new ScimException(404, "invalidValue",
                            "User not found: " + memberValue));
        }

        ScimGroupMembership membership = new ScimGroupMembership();
        membership.setGroup(group);
        membership.setMemberValue(memberId);
        membership.setMemberType(memberType);

        // Set display if provided
        if (memberMap.get("display") != null) {
            membership.setDisplay(memberMap.get("display").toString());
        } else if ("User".equalsIgnoreCase(memberType)) {
            userRepository.findByIdAndWorkspaceId(memberId, workspaceId)
                    .ifPresent(u -> membership.setDisplay(u.getDisplayName() != null
                            ? u.getDisplayName()
                            : u.getUserName()));
        }

        group.getMembers().add(membership);
    }

    private String extractFilterValue(String filterExpr) {
        // Parse: value eq "uuid"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("value\\s+eq\\s+\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(filterExpr);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
