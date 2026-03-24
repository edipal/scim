package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.ScimRequestLog;
import de.palsoftware.scim.server.common.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresDataJpaTest
class ScimRequestLogRepositoryTest extends PostgresRepositoryTestSupport {

    @Autowired
    private ScimRequestLogRepository repository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setName("WS");
        workspace = workspaceRepository.saveAndFlush(workspace);

        Workspace ws2 = new Workspace();
        ws2.setName("WS2");
        ws2 = workspaceRepository.saveAndFlush(ws2);

        ScimRequestLog log1 = new ScimRequestLog();
        log1.setWorkspace(workspace);
        log1.setMethod("GET");
        log1.setPath("/Users");
        repository.saveAndFlush(log1);

        ScimRequestLog log2 = new ScimRequestLog();
        log2.setWorkspace(workspace);
        log2.setMethod("POST");
        log2.setPath("/Groups");
        repository.saveAndFlush(log2);
        
        ScimRequestLog log3 = new ScimRequestLog();
        log3.setWorkspace(ws2);
        log3.setMethod("DELETE");
        log3.setPath("/Users/123");
        repository.saveAndFlush(log3);
    }

    @Test
    void findByWorkspace_IdOrderByCreatedAtDesc_returnsPage() {
        Page<ScimRequestLog> page = repository.findByWorkspace_IdOrderByCreatedAtDesc(workspace.getId(), PageRequest.of(0, 5));
        
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        // By default should be ordered, testing that it executes successfully
    }

    @Test
    void deleteByWorkspaceId_deletesLogsOnlyForWorkspace() {
        long deleted = repository.deleteByWorkspaceId(workspace.getId());
        
        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll()).hasSize(1);
    }
}
