package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.ScimGroup;
import de.palsoftware.scim.server.common.model.ScimGroupMembership;
import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.common.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ScimGroupMembershipRepositoryTest {

    @Autowired
    private ScimGroupMembershipRepository repository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ScimGroupRepository groupRepository;

    @Autowired
    private ScimUserRepository userRepository;

    private ScimGroup group1;
    private ScimGroup group2;
    private ScimUser user1;

    @BeforeEach
    void setUp() {
        Workspace ws = new Workspace();
        ws.setName("WS");
        ws = workspaceRepository.saveAndFlush(ws);

        group1 = new ScimGroup();
        group1.setWorkspace(ws);
        group1.setDisplayName("Group 1");
        group1 = groupRepository.saveAndFlush(group1);

        group2 = new ScimGroup();
        group2.setWorkspace(ws);
        group2.setDisplayName("Group 2");
        group2 = groupRepository.saveAndFlush(group2);

        user1 = new ScimUser();
        user1.setWorkspace(ws);
        user1.setUserName("jsmith");
        user1 = userRepository.saveAndFlush(user1);

        ScimGroupMembership mem1 = new ScimGroupMembership();
        mem1.setGroup(group1);
        mem1.setMemberValue(user1.getId());
        mem1.setMemberType("User");
        repository.saveAndFlush(mem1);

        ScimGroupMembership mem2 = new ScimGroupMembership();
        mem2.setGroup(group2);
        mem2.setMemberValue(user1.getId());
        mem2.setMemberType("User");
        repository.saveAndFlush(mem2);
    }

    @Test
    void findByMemberValue_returnsMembershipsAndFetchesGroup() {
        List<ScimGroupMembership> memberships = repository.findByMemberValue(user1.getId());
        
        assertThat(memberships).hasSize(2);
        // Verify joined group is accessible
        assertThat(memberships.get(0).getGroup().getDisplayName()).isNotNull();
    }

    @Test
    void findByMemberValueIn_returnsMemberships() {
        List<ScimGroupMembership> memberships = repository.findByMemberValueIn(List.of(user1.getId(), UUID.randomUUID()));
        assertThat(memberships).hasSize(2);
    }

    @Test
    void deleteByMemberValue_removesMemberships() {
        long deleted = repository.deleteByMemberValue(user1.getId());
        assertThat(deleted).isEqualTo(2);
        
        assertThat(repository.findByMemberValue(user1.getId())).isEmpty();
    }

    @Test
    void deleteByMemberValueIn_removesMemberships() {
        long deleted = repository.deleteByMemberValueIn(List.of(user1.getId(), UUID.randomUUID()));
        assertThat(deleted).isEqualTo(2);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void deleteByGroupId_removesGroupMemberships() {
        long deleted = repository.deleteByGroupId(group1.getId());
        assertThat(deleted).isEqualTo(1);
        
        List<ScimGroupMembership> memberships = repository.findAll();
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getGroup().getId()).isEqualTo(group2.getId());
    }

    @Test
    void deleteByGroupIdIn_removesAssociatedMemberships() {
        long deleted = repository.deleteByGroupIdIn(List.of(group1.getId(), group2.getId()));
        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll()).isEmpty();
    }
}
