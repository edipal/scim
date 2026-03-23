package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.ScimGroup;
import de.palsoftware.scim.server.common.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresDataJpaTest
class ScimGroupRepositoryTest extends PostgresRepositoryTestSupport {

    private static final String ADMINS = "Admins";
    private static final String USERS = "Users";
    private static final String EXT_2 = "ext-2";
    private static final String ADMIN_QUERY = "admin";

    @Autowired
    private ScimGroupRepository repository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;
    private Workspace workspace2;
    private ScimGroup group1;
    private ScimGroup group2;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setName("WS1");
        workspace = workspaceRepository.saveAndFlush(workspace);

        workspace2 = new Workspace();
        workspace2.setName("WS2");
        workspace2 = workspaceRepository.saveAndFlush(workspace2);

        group1 = new ScimGroup();
        group1.setWorkspace(workspace);
        group1.setDisplayName(ADMINS);
        group1.setExternalId("ext-1");
        group1 = repository.saveAndFlush(group1);

        group2 = new ScimGroup();
        group2.setWorkspace(workspace);
        group2.setDisplayName(USERS);
        group2.setExternalId(EXT_2);
        group2 = repository.saveAndFlush(group2);
        
        ScimGroup group3 = new ScimGroup();
        group3.setWorkspace(workspace2);
        group3.setDisplayName("other");
        repository.saveAndFlush(group3);
    }

    @Test
    void findByIdAndWorkspaceIdFindsCorrectGroup() {
        Optional<ScimGroup> result = repository.findByIdAndWorkspaceId(group1.getId(), workspace.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getDisplayName()).isEqualTo(ADMINS);

        Optional<ScimGroup> missing = repository.findByIdAndWorkspaceId(group1.getId(), workspace2.getId());
        assertThat(missing).isEmpty();
    }

    @Test
    void findByDisplayNameAndWorkspaceIdFindsMatch() {
        Optional<ScimGroup> result = repository.findByDisplayNameAndWorkspaceId(ADMINS, workspace.getId());
        assertThat(result).isPresent();
        
        Optional<ScimGroup> missing = repository.findByDisplayNameAndWorkspaceId("admins", workspace2.getId()); // Exact match requested in repo method (case sensitive unless ignore case specified)
        assertThat(missing).isEmpty();
    }

    @Test
    void existsByDisplayNameAndWorkspaceIdChecksExistence() {
        assertThat(repository.existsByDisplayNameAndWorkspaceId(ADMINS, workspace.getId())).isTrue();
        assertThat(repository.existsByDisplayNameAndWorkspaceId("admins", workspace.getId())).isFalse(); // Not ignore case
    }

    @Test
    void findByWorkspaceIdListsAllForWorkspace() {
        List<ScimGroup> groups = repository.findByWorkspaceId(workspace.getId());
        assertThat(groups).hasSize(2);
    }

    @Test
    void findByWorkspaceIdPageableReturnsPaged() {
        Page<ScimGroup> page = repository.findByWorkspaceId(workspace.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void searchByWorkspaceIdAndDisplayNameOrExternalIdFiltersCorrectly() {
        // Match displayName part
        Page<ScimGroup> page1 = repository.findByWorkspaceIdAndDisplayNameContainingIgnoreCaseOrWorkspaceIdAndExternalIdContainingIgnoreCase(
                workspace.getId(), ADMIN_QUERY, workspace.getId(), ADMIN_QUERY, PageRequest.of(0, 10));
        
        assertThat(page1.getTotalElements()).isEqualTo(1);
        assertThat(page1.getContent().get(0).getDisplayName()).isEqualTo(ADMINS);

        // Match externalId part
        Page<ScimGroup> page2 = repository.findByWorkspaceIdAndDisplayNameContainingIgnoreCaseOrWorkspaceIdAndExternalIdContainingIgnoreCase(
                workspace.getId(), EXT_2, workspace.getId(), EXT_2, PageRequest.of(0, 10));
        
        assertThat(page2.getTotalElements()).isEqualTo(1);
        assertThat(page2.getContent().get(0).getDisplayName()).isEqualTo(USERS);
    }

    @Test
    void countByWorkspaceIdReturnsCount() {
        assertThat(repository.countByWorkspaceId(workspace.getId())).isEqualTo(2);
        assertThat(repository.countByWorkspaceId(workspace2.getId())).isEqualTo(1);
    }

    @Test
    void findIdsByWorkspaceIdReturnsIds() {
        List<UUID> ids = repository.findIdsByWorkspaceId(workspace.getId());
        assertThat(ids).hasSize(2).contains(group1.getId(), group2.getId());
    }

    @Test
    void deleteAllByWorkspaceIdDeletesOnlyForGivenWorkspace() {
        int deleted = repository.deleteAllByWorkspaceId(workspace.getId());
        assertThat(deleted).isEqualTo(2);
        
        assertThat(repository.countByWorkspaceId(workspace.getId())).isZero();
        assertThat(repository.countByWorkspaceId(workspace2.getId())).isEqualTo(1);
    }
}
