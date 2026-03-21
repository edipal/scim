package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.Workspace;
import de.palsoftware.scim.server.common.model.WorkspaceToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkspaceTokenRepositoryTest {

    @Autowired
    private WorkspaceTokenRepository repository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;
    private WorkspaceToken activeToken;
    private WorkspaceToken revokedToken;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setName("WS");
        workspace = workspaceRepository.saveAndFlush(workspace);

        activeToken = new WorkspaceToken();
        activeToken.setWorkspace(workspace);
        activeToken.setName("Primary Token");
        activeToken.setTokenHash("hash123");
        activeToken.setRevoked(false);
        activeToken = repository.saveAndFlush(activeToken);

        revokedToken = new WorkspaceToken();
        revokedToken.setWorkspace(workspace);
        revokedToken.setName("Old Token");
        revokedToken.setTokenHash("hash456");
        revokedToken.setRevoked(true);
        revokedToken = repository.saveAndFlush(revokedToken);
    }

    @Test
    void findByTokenHashAndNotRevoked_returnsActiveToken() {
        Optional<WorkspaceToken> found = repository.findByTokenHashAndNotRevoked("hash123");
        assertThat(found).isPresent();
        // Since it joins fetch, verify workspace
        assertThat(found.get().getWorkspace().getName()).isEqualTo("WS");

        Optional<WorkspaceToken> missing = repository.findByTokenHashAndNotRevoked("hash456");
        assertThat(missing).isEmpty();
    }

    @Test
    void findByTokenHash_returnsEvenIfRevoked() {
        assertThat(repository.findByTokenHash("hash123")).isPresent();
        assertThat(repository.findByTokenHash("hash456")).isPresent();
        assertThat(repository.findByTokenHash("unknown")).isEmpty();
    }

    @Test
    void findByWorkspaceId_returnsTokensForWorkspace() {
        List<WorkspaceToken> tokens = repository.findByWorkspaceId(workspace.getId());
        assertThat(tokens).hasSize(2);
    }

    @Test
    void findByIdAndWorkspaceId_returnsMatch() {
        assertThat(repository.findByIdAndWorkspaceId(activeToken.getId(), workspace.getId())).isPresent();
        assertThat(repository.findByIdAndWorkspaceId(activeToken.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByWorkspaceIdAndName_findsCorrectToken() {
        assertThat(repository.findByWorkspaceIdAndName(workspace.getId(), "Primary Token")).isPresent();
        assertThat(repository.findByWorkspaceIdAndName(workspace.getId(), "Missing Token")).isEmpty();
    }

    @Test
    void deleteByWorkspaceId_removesWorkspaceTokens() {
        long deleted = repository.deleteByWorkspaceId(workspace.getId());
        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll()).isEmpty();
    }
}
