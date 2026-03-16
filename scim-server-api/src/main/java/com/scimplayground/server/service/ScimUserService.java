package com.scimplayground.server.service;

import com.scimplayground.server.model.*;
import com.scimplayground.server.repository.*;
import com.scimplayground.server.scim.error.ScimException;
import com.scimplayground.server.scim.filter.ScimFilterParser;
import com.scimplayground.server.scim.mapper.ScimUserMapper;
import com.scimplayground.server.scim.patch.ScimPatchEngine;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ScimUserService {

    private final ScimUserRepository userRepository;
    private final ScimGroupMembershipRepository membershipRepository;
    private final WorkspaceRepository workspaceRepository;

    public ScimUserService(ScimUserRepository userRepository,
                            ScimGroupMembershipRepository membershipRepository,
                            WorkspaceRepository workspaceRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public ScimUser createUser(UUID workspaceId, Map<String, Object> input) {
        String userName = (String) input.get("userName");
        if (userName == null || userName.isBlank()) {
            throw new ScimException(400, "invalidValue", "userName is required");
        }

        if (userRepository.existsByUserNameIgnoreCaseAndWorkspaceId(userName, workspaceId)) {
            throw new ScimException(409, "uniqueness", "User with userName '" + userName + "' already exists");
        }

        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ScimException(404, null, "Workspace not found"));

        ScimUser user = new ScimUser();
        user.setWorkspace(ws);
        ScimUserMapper.applyFromScimInput(user, input);

        return userRepository.save(user);
    }

    public ScimUser getUser(UUID workspaceId, UUID userId) {
        return userRepository.findByIdAndWorkspaceId(userId, workspaceId)
                .orElseThrow(() -> new ScimException(404, null, "User not found: " + userId));
    }

    public Map<String, Object> listUsers(UUID workspaceId, String filter, String sortBy,
                                          String sortOrder, int startIndex, int count) {
        Specification<ScimUser> spec = ScimFilterParser.parseUserFilter(filter, workspaceId);

        // Get total count
        long totalResults = userRepository.count(spec);

        if (count == 0) {
            return buildListResponse(Collections.emptyList(), totalResults, startIndex, 0);
        }

        // Sorting
        String sortAttr = ScimFilterParser.resolveUserSortAttribute(sortBy);
        Sort.Direction direction = "descending".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortAttr);

        // Pagination (SCIM is 1-based, Spring is 0-based)
        int offset = Math.max(0, startIndex - 1);

        // We need offset-based paging, not page-based
        List<ScimUser> allMatching = userRepository.findAll(spec, sort);
        List<ScimUser> page;
        if (offset >= allMatching.size()) {
            page = Collections.emptyList();
        } else {
            int end = Math.min(offset + count, allMatching.size());
            page = allMatching.subList(offset, end);
        }

        return buildListResponse(page, totalResults, startIndex, page.size());
    }

    private Map<String, Object> buildListResponse(List<ScimUser> users, long totalResults,
                                                    int startIndex, int itemsPerPage) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        response.put("totalResults", (int) totalResults);
        response.put("startIndex", startIndex);
        response.put("itemsPerPage", itemsPerPage);
        // Resources will be populated by the controller with full SCIM representations
        response.put("Resources", users);
        return response;
    }

    public ScimUser replaceUser(UUID workspaceId, UUID userId, Map<String, Object> input) {
        ScimUser existing = getUser(workspaceId, userId);

        // Validate userName
        String newUserName = (String) input.get("userName");
        if (newUserName == null || newUserName.isBlank()) {
            throw new ScimException(400, "invalidValue", "userName is required");
        }

        // Check if id in body conflicts
        Object bodyId = input.get("id");
        if (bodyId != null && !userId.toString().equals(bodyId.toString())) {
            // Per RFC, either reject or ignore. We'll ignore the id mismatch for PUT.
        }

        // Check uniqueness if userName changed
        if (!existing.getUserName().equalsIgnoreCase(newUserName)
                && userRepository.existsByUserNameIgnoreCaseAndWorkspaceId(newUserName, workspaceId)) {
            throw new ScimException(409, "uniqueness", "User with userName '" + newUserName + "' already exists");
        }

        // PUT = full replacement
        ScimUserMapper.clearMutableAttributes(existing);
        ScimUserMapper.applyFromScimInput(existing, input);

        return userRepository.save(existing);
    }

    public ScimUser patchUser(UUID workspaceId, UUID userId, List<Map<String, Object>> operations) {
        ScimUser user = getUser(workspaceId, userId);
        ScimPatchEngine.applyPatchOperations(user, operations);
        return userRepository.save(user);
    }

    public void deleteUser(UUID workspaceId, UUID userId) {
        ScimUser user = getUser(workspaceId, userId);
        // Remove user from all groups
        membershipRepository.deleteByMemberValue(userId);
        userRepository.delete(user);
    }

    /**
     * Get groups this user belongs to (for the read-only 'groups' attribute).
     */
    public List<Map<String, Object>> getUserGroups(UUID userId, String baseUrl) {
        List<ScimGroupMembership> memberships = membershipRepository.findByMemberValue(userId);
        return memberships.stream()
                .map(m -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("value", m.getGroup().getId().toString());
                    g.put("$ref", baseUrl + "/Groups/" + m.getGroup().getId());
                    g.put("display", m.getGroup().getDisplayName());
                    g.put("type", "direct");
                    return g;
                })
                .toList();
    }
}
