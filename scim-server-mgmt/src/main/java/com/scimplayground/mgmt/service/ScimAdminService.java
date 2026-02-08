package com.scimplayground.mgmt.service;

import com.scimplayground.mgmt.dto.GroupUpsertRequest;
import com.scimplayground.mgmt.dto.UserUpsertRequest;
import com.scimplayground.server.model.ScimGroup;
import com.scimplayground.server.model.ScimGroupMembership;
import com.scimplayground.server.model.ScimUser;
import com.scimplayground.server.model.ScimUserAddress;
import com.scimplayground.server.model.ScimUserEmail;
import com.scimplayground.server.model.ScimUserEntitlement;
import com.scimplayground.server.model.ScimUserIm;
import com.scimplayground.server.model.ScimUserPhoneNumber;
import com.scimplayground.server.model.ScimUserPhoto;
import com.scimplayground.server.model.ScimUserRole;
import com.scimplayground.server.model.ScimUserX509Certificate;
import com.scimplayground.server.model.Workspace;
import com.scimplayground.server.repository.ScimGroupMembershipRepository;
import com.scimplayground.server.repository.ScimGroupRepository;
import com.scimplayground.server.repository.ScimUserRepository;
import com.scimplayground.server.repository.WorkspaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScimAdminService {

    private final WorkspaceRepository workspaceRepository;
    private final ScimUserRepository userRepository;
    private final ScimGroupRepository groupRepository;
    private final ScimGroupMembershipRepository membershipRepository;

    public ScimAdminService(WorkspaceRepository workspaceRepository,
                             ScimUserRepository userRepository,
                             ScimGroupRepository groupRepository,
                             ScimGroupMembershipRepository membershipRepository) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    public List<ScimUser> listUsers(UUID workspaceId, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        return userRepository.findByWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(u -> safeLower(u.getUserName())))
                .toList();
    }

    public Page<ScimUser> listUsersPage(UUID workspaceId, String query, Pageable pageable, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        if (query == null || query.isBlank()) {
            return userRepository.findByWorkspaceId(workspaceId, pageable);
        }
        return userRepository.findByWorkspaceIdAndUserNameContainingIgnoreCase(workspaceId, query, pageable);
    }

    public ScimUser createUser(UUID workspaceId, UserUpsertRequest request, String actorUsername, boolean admin) {
        String userName = normalizeRequired("userName", request.userName());
        if (userRepository.existsByUserNameIgnoreCaseAndWorkspaceId(userName, workspaceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with userName '" + userName + "' already exists");
        }

        Workspace ws = ensureWorkspaceAccess(workspaceId, actorUsername, admin);

        ScimUser user = new ScimUser();
        user.setWorkspace(ws);
        user.setUserName(userName);
        applyUserFields(user, request, true);

        return userRepository.save(user);
    }

    public ScimUser updateUser(UUID workspaceId, UUID userId, UserUpsertRequest request, String actorUsername, boolean admin) {
        ScimUser user = getUser(workspaceId, userId, actorUsername, admin);

        if (request.userName() != null) {
            String userName = normalizeRequired("userName", request.userName());
            if (!user.getUserName().equalsIgnoreCase(userName)
                    && userRepository.existsByUserNameIgnoreCaseAndWorkspaceId(userName, workspaceId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User with userName '" + userName + "' already exists");
            }
            user.setUserName(userName);
        }

        applyUserFields(user, request, false);

        return userRepository.save(user);
    }

    public void deleteUser(UUID workspaceId, UUID userId, String actorUsername, boolean admin) {
        ScimUser user = getUser(workspaceId, userId, actorUsername, admin);
        membershipRepository.deleteByMemberValue(userId);
        userRepository.delete(user);
    }

    public void deleteAllUsers(UUID workspaceId, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        List<ScimUser> users = userRepository.findByWorkspaceId(workspaceId);
        if (!users.isEmpty()) {
            List<UUID> userIds = users.stream()
                    .map(ScimUser::getId)
                    .toList();
            membershipRepository.deleteByMemberValueIn(userIds);
            userRepository.deleteAll(users);
        }
    }

    public List<ScimGroup> listGroups(UUID workspaceId, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        return groupRepository.findByWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(g -> safeLower(g.getDisplayName())))
                .toList();
    }

    public Page<ScimGroup> listGroupsPage(UUID workspaceId, String query, Pageable pageable, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        if (query == null || query.isBlank()) {
            return groupRepository.findByWorkspaceId(workspaceId, pageable);
        }
        return groupRepository.findByWorkspaceIdAndDisplayNameContainingIgnoreCaseOrWorkspaceIdAndExternalIdContainingIgnoreCase(
                workspaceId,
                query,
                workspaceId,
                query,
                pageable);
    }

    public ScimGroup createGroup(UUID workspaceId, GroupUpsertRequest request, String actorUsername, boolean admin) {
        String displayName = normalizeRequired("displayName", request.displayName());
        if (groupRepository.existsByDisplayNameAndWorkspaceId(displayName, workspaceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Group with displayName '" + displayName + "' already exists");
        }

        Workspace ws = ensureWorkspaceAccess(workspaceId, actorUsername, admin);

        ScimGroup group = new ScimGroup();
        group.setWorkspace(ws);
        group.setDisplayName(displayName);
        applyGroupFields(group, request, true);

        return groupRepository.save(group);
    }

    public ScimGroup updateGroup(UUID workspaceId, UUID groupId, GroupUpsertRequest request, String actorUsername, boolean admin) {
        ScimGroup group = getGroup(workspaceId, groupId, actorUsername, admin);

        if (request.displayName() != null) {
            String displayName = normalizeRequired("displayName", request.displayName());
            if (!group.getDisplayName().equals(displayName)
                    && groupRepository.existsByDisplayNameAndWorkspaceId(displayName, workspaceId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Group with displayName '" + displayName + "' already exists");
            }
            group.setDisplayName(displayName);
        }

        applyGroupFields(group, request, false);

        return groupRepository.save(group);
    }

    public void deleteGroup(UUID workspaceId, UUID groupId, String actorUsername, boolean admin) {
        ScimGroup group = getGroup(workspaceId, groupId, actorUsername, admin);
        membershipRepository.deleteByMemberValue(groupId);
        membershipRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);
    }

    public void deleteAllGroups(UUID workspaceId, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        List<ScimGroup> groups = groupRepository.findByWorkspaceId(workspaceId);
        if (!groups.isEmpty()) {
            List<UUID> groupIds = groups.stream()
                    .map(ScimGroup::getId)
                    .toList();
            membershipRepository.deleteByMemberValueIn(groupIds);
            membershipRepository.deleteByGroupIdIn(groupIds);
            groupRepository.deleteAll(groups);
        }
    }

    private Workspace ensureWorkspaceAccess(UUID workspaceId, String actorUsername, boolean admin) {
        if (admin) {
            return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        }
        return workspaceRepository.findByIdAndCreatedByUsername(workspaceId, actorUsername)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    }

    private ScimUser getUser(UUID workspaceId, UUID userId, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        return userRepository.findByIdAndWorkspaceId(userId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private ScimGroup getGroup(UUID workspaceId, UUID groupId, String actorUsername, boolean admin) {
        ensureWorkspaceAccess(workspaceId, actorUsername, admin);
        return groupRepository.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applyUserFields(ScimUser user, UserUpsertRequest request, boolean isCreate) {
        if (isCreate || request.displayName() != null) {
            user.setDisplayName(normalizeOptional(request.displayName()));
        }
        if (isCreate || request.externalId() != null) {
            user.setExternalId(normalizeOptional(request.externalId()));
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (isCreate || request.nameFormatted() != null) {
            user.setNameFormatted(normalizeOptional(request.nameFormatted()));
        }
        if (isCreate || request.nameFamilyName() != null) {
            user.setNameFamilyName(normalizeOptional(request.nameFamilyName()));
        }
        if (isCreate || request.nameGivenName() != null) {
            user.setNameGivenName(normalizeOptional(request.nameGivenName()));
        }
        if (isCreate || request.nameMiddleName() != null) {
            user.setNameMiddleName(normalizeOptional(request.nameMiddleName()));
        }
        if (isCreate || request.nameHonorificPrefix() != null) {
            user.setNameHonorificPrefix(normalizeOptional(request.nameHonorificPrefix()));
        }
        if (isCreate || request.nameHonorificSuffix() != null) {
            user.setNameHonorificSuffix(normalizeOptional(request.nameHonorificSuffix()));
        }
        if (isCreate || request.nickName() != null) {
            user.setNickName(normalizeOptional(request.nickName()));
        }
        if (isCreate || request.profileUrl() != null) {
            user.setProfileUrl(normalizeOptional(request.profileUrl()));
        }
        if (isCreate || request.title() != null) {
            user.setTitle(normalizeOptional(request.title()));
        }
        if (isCreate || request.userType() != null) {
            user.setUserType(normalizeOptional(request.userType()));
        }
        if (isCreate || request.preferredLanguage() != null) {
            user.setPreferredLanguage(normalizeOptional(request.preferredLanguage()));
        }
        if (isCreate || request.locale() != null) {
            user.setLocale(normalizeOptional(request.locale()));
        }
        if (isCreate || request.timezone() != null) {
            user.setTimezone(normalizeOptional(request.timezone()));
        }
        if (isCreate || request.password() != null) {
            user.setPassword(normalizeOptional(request.password()));
        }
        if (isCreate || request.enterpriseEmployeeNumber() != null) {
            user.setEnterpriseEmployeeNumber(normalizeOptional(request.enterpriseEmployeeNumber()));
        }
        if (isCreate || request.enterpriseCostCenter() != null) {
            user.setEnterpriseCostCenter(normalizeOptional(request.enterpriseCostCenter()));
        }
        if (isCreate || request.enterpriseOrganization() != null) {
            user.setEnterpriseOrganization(normalizeOptional(request.enterpriseOrganization()));
        }
        if (isCreate || request.enterpriseDivision() != null) {
            user.setEnterpriseDivision(normalizeOptional(request.enterpriseDivision()));
        }
        if (isCreate || request.enterpriseDepartment() != null) {
            user.setEnterpriseDepartment(normalizeOptional(request.enterpriseDepartment()));
        }
        if (isCreate || request.enterpriseManagerValue() != null) {
            user.setEnterpriseManagerValue(normalizeOptional(request.enterpriseManagerValue()));
        }
        if (isCreate || request.enterpriseManagerRef() != null) {
            user.setEnterpriseManagerRef(normalizeOptional(request.enterpriseManagerRef()));
        }
        if (isCreate || request.enterpriseManagerDisplay() != null) {
            user.setEnterpriseManagerDisplay(normalizeOptional(request.enterpriseManagerDisplay()));
        }

        if (request.emails() != null) {
            user.getEmails().clear();
            for (UserUpsertRequest.MultiValue mv : request.emails()) {
                ScimUserEmail email = new ScimUserEmail();
                email.setUser(user);
                email.setValue(normalizeOptional(mv.value()));
                email.setType(normalizeOptional(mv.type()));
                email.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    email.setPrimaryFlag(mv.primary());
                }
                user.getEmails().add(email);
            }
        }
        if (request.phoneNumbers() != null) {
            user.getPhoneNumbers().clear();
            for (UserUpsertRequest.MultiValue mv : request.phoneNumbers()) {
                ScimUserPhoneNumber phone = new ScimUserPhoneNumber();
                phone.setUser(user);
                phone.setValue(normalizeOptional(mv.value()));
                phone.setType(normalizeOptional(mv.type()));
                phone.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    phone.setPrimaryFlag(mv.primary());
                }
                user.getPhoneNumbers().add(phone);
            }
        }
        if (request.addresses() != null) {
            user.getAddresses().clear();
            for (UserUpsertRequest.Address addr : request.addresses()) {
                ScimUserAddress address = new ScimUserAddress();
                address.setUser(user);
                address.setFormatted(normalizeOptional(addr.formatted()));
                address.setStreetAddress(normalizeOptional(addr.streetAddress()));
                address.setLocality(normalizeOptional(addr.locality()));
                address.setRegion(normalizeOptional(addr.region()));
                address.setPostalCode(normalizeOptional(addr.postalCode()));
                address.setCountry(normalizeOptional(addr.country()));
                address.setType(normalizeOptional(addr.type()));
                if (addr.primary() != null) {
                    address.setPrimaryFlag(addr.primary());
                }
                user.getAddresses().add(address);
            }
        }
        if (request.entitlements() != null) {
            user.getEntitlements().clear();
            for (UserUpsertRequest.MultiValue mv : request.entitlements()) {
                ScimUserEntitlement entitlement = new ScimUserEntitlement();
                entitlement.setUser(user);
                entitlement.setValue(normalizeOptional(mv.value()));
                entitlement.setType(normalizeOptional(mv.type()));
                entitlement.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    entitlement.setPrimaryFlag(mv.primary());
                }
                user.getEntitlements().add(entitlement);
            }
        }
        if (request.roles() != null) {
            user.getRoles().clear();
            for (UserUpsertRequest.MultiValue mv : request.roles()) {
                ScimUserRole role = new ScimUserRole();
                role.setUser(user);
                role.setValue(normalizeOptional(mv.value()));
                role.setType(normalizeOptional(mv.type()));
                role.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    role.setPrimaryFlag(mv.primary());
                }
                user.getRoles().add(role);
            }
        }
        if (request.ims() != null) {
            user.getIms().clear();
            for (UserUpsertRequest.MultiValue mv : request.ims()) {
                ScimUserIm im = new ScimUserIm();
                im.setUser(user);
                im.setValue(normalizeOptional(mv.value()));
                im.setType(normalizeOptional(mv.type()));
                im.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    im.setPrimaryFlag(mv.primary());
                }
                user.getIms().add(im);
            }
        }
        if (request.photos() != null) {
            user.getPhotos().clear();
            for (UserUpsertRequest.MultiValue mv : request.photos()) {
                ScimUserPhoto photo = new ScimUserPhoto();
                photo.setUser(user);
                photo.setValue(normalizeOptional(mv.value()));
                photo.setType(normalizeOptional(mv.type()));
                photo.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    photo.setPrimaryFlag(mv.primary());
                }
                user.getPhotos().add(photo);
            }
        }
        if (request.x509Certificates() != null) {
            user.getX509Certificates().clear();
            for (UserUpsertRequest.MultiValue mv : request.x509Certificates()) {
                ScimUserX509Certificate cert = new ScimUserX509Certificate();
                cert.setUser(user);
                cert.setValue(normalizeOptional(mv.value()));
                cert.setType(normalizeOptional(mv.type()));
                cert.setDisplay(normalizeOptional(mv.display()));
                if (mv.primary() != null) {
                    cert.setPrimaryFlag(mv.primary());
                }
                user.getX509Certificates().add(cert);
            }
        }
    }

    private void applyGroupFields(ScimGroup group, GroupUpsertRequest request, boolean isCreate) {
        if (isCreate || request.externalId() != null) {
            group.setExternalId(normalizeOptional(request.externalId()));
        }
        if (request.members() != null) {
            group.getMembers().clear();
            for (GroupUpsertRequest.Member member : request.members()) {
                if (member == null || member.value() == null || member.value().isBlank()) {
                    continue;
                }
                ScimGroupMembership membership = new ScimGroupMembership();
                membership.setGroup(group);
                try {
                    membership.setMemberValue(UUID.fromString(member.value().trim()));
                } catch (IllegalArgumentException ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member UUID: " + member.value());
                }
                String memberType = normalizeOptional(member.type());
                membership.setMemberType(memberType != null ? memberType : "User");
                if ("Group".equalsIgnoreCase(membership.getMemberType())
                        && group.getId() != null
                        && group.getId().equals(membership.getMemberValue())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group cannot include itself as a member");
                }
                membership.setDisplay(normalizeOptional(member.display()));
                group.getMembers().add(membership);
            }
        }
    }

    private String normalizeRequired(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
