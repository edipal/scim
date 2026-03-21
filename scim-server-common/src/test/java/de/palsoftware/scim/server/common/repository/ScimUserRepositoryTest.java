package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.common.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ScimUserRepositoryTest {

    @Autowired
    private ScimUserRepository repository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;
    private Workspace workspace2;
    private ScimUser user1;
    private ScimUser user2;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setName("WS1");
        workspace = workspaceRepository.saveAndFlush(workspace);

        workspace2 = new Workspace();
        workspace2.setName("WS2");
        workspace2 = workspaceRepository.saveAndFlush(workspace2);

        user1 = new ScimUser();
        user1.setWorkspace(workspace);
        user1.setUserName("jsmith");
        user1 = repository.saveAndFlush(user1);

        user2 = new ScimUser();
        user2.setWorkspace(workspace);
        user2.setUserName("bjohnson");
        user2 = repository.saveAndFlush(user2);
        
        ScimUser user3 = new ScimUser();
        user3.setWorkspace(workspace2);
        user3.setUserName("other");
        repository.saveAndFlush(user3);
    }

    @Test
    void findByIdAndWorkspaceId_findsCorrectUser() {
        Optional<ScimUser> result = repository.findByIdAndWorkspaceId(user1.getId(), workspace.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getUserName()).isEqualTo("jsmith");

        Optional<ScimUser> missing = repository.findByIdAndWorkspaceId(user1.getId(), workspace2.getId());
        assertThat(missing).isEmpty();
    }

    @Test
    void findByUserNameIgnoreCaseAndWorkspaceId_findsIgnoringCase() {
        Optional<ScimUser> result = repository.findByUserNameIgnoreCaseAndWorkspaceId("JSmith", workspace.getId());
        assertThat(result).isPresent();
        
        Optional<ScimUser> missing = repository.findByUserNameIgnoreCaseAndWorkspaceId("jsmith", workspace2.getId());
        assertThat(missing).isEmpty();
    }

    @Test
    void existsByUserNameIgnoreCaseAndWorkspaceId_checksExistence() {
        assertThat(repository.existsByUserNameIgnoreCaseAndWorkspaceId("JSMITH", workspace.getId())).isTrue();
        assertThat(repository.existsByUserNameIgnoreCaseAndWorkspaceId("unknown", workspace.getId())).isFalse();
    }

    @Test
    void findByWorkspaceId_listsAllForWorkspace() {
        List<ScimUser> users = repository.findByWorkspaceId(workspace.getId());
        assertThat(users).hasSize(2);
    }

    @Test
    void findByWorkspaceId_pageable_returnsPaged() {
        Page<ScimUser> page = repository.findByWorkspaceId(workspace.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findByWorkspaceIdAndUserNameContainingIgnoreCase_filtersCorrectly() {
        Page<ScimUser> page = repository.findByWorkspaceIdAndUserNameContainingIgnoreCase(
                workspace.getId(), "smith", PageRequest.of(0, 10));
        
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getUserName()).isEqualTo("jsmith");
    }

    @Test
    void countByWorkspaceId_returnsCount() {
        assertThat(repository.countByWorkspaceId(workspace.getId())).isEqualTo(2);
        assertThat(repository.countByWorkspaceId(workspace2.getId())).isEqualTo(1);
    }

    @Test
    void findIdsByWorkspaceId_returnsIds() {
        List<UUID> ids = repository.findIdsByWorkspaceId(workspace.getId());
        assertThat(ids).hasSize(2).contains(user1.getId(), user2.getId());
    }

    @Test
    void deleteAllByWorkspaceId_deletesOnlyForGivenWorkspace() {
        int deleted = repository.deleteAllByWorkspaceId(workspace.getId());
        assertThat(deleted).isEqualTo(2);
        
        assertThat(repository.countByWorkspaceId(workspace.getId())).isZero();
        assertThat(repository.countByWorkspaceId(workspace2.getId())).isEqualTo(1);
    }
}
