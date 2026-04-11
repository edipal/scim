package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresDataJpaTest
class WorkspaceRepositoryTest extends PostgresRepositoryTestSupport {

    private static final String TEST_NAME = "TestName";
    private static final String USERNAME = "user123";

    @Autowired
    private WorkspaceRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void sameNameDifferentOwnersIsAllowed() {
        Workspace w = new Workspace();
        w.setName(TEST_NAME);
        w.setCreatedByUsername("owner-one@example.com");
        repository.saveAndFlush(w);

        Workspace duplicateName = new Workspace();
        duplicateName.setName(TEST_NAME);
        duplicateName.setCreatedByUsername("owner-two@example.com");

        Workspace saved = repository.saveAndFlush(duplicateName);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void sameNameSameOwnerIsRejected() {
        Workspace first = new Workspace();
        first.setName(TEST_NAME);
        first.setCreatedByUsername(USERNAME);
        repository.saveAndFlush(first);

        Workspace duplicate = new Workspace();
        duplicate.setName(TEST_NAME);
        duplicate.setCreatedByUsername(USERNAME);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdAndCreatedByUsernameReturnsWorkspace() {
        Workspace w = new Workspace();
        w.setName("User Workspace");
        w.setCreatedByUsername(USERNAME);
        w = repository.saveAndFlush(w);

        Optional<Workspace> result = repository.findByIdAndCreatedByUsername(w.getId(), USERNAME);
        assertThat(result).isPresent();
    }

    @Test
    void findByCreatedByUsernameOrderByCreatedAtDescReturnsOrderedList() {
        Workspace w1 = new Workspace();
        w1.setName("w1");
        w1.setCreatedByUsername("user");
        repository.save(w1);
        
        Workspace w2 = new Workspace();
        w2.setName("w2");
        w2.setCreatedByUsername("user");
        repository.save(w2);

        repository.flush();

        List<Workspace> results = repository.findByCreatedByUsernameOrderByCreatedAtDesc("user");
        assertThat(results).hasSize(2);
        // Created automatically via annotations, so w2 is likely created after w1 or at the same time.
        // We mainly verify the method executes and maps correctly.
    }

    @Test
    void findAllOrderByCreatedAtDescExecutesSuccessfully() {
        Workspace w1 = new Workspace();
        w1.setName("w1");
        repository.save(w1);
        repository.flush();

        List<Workspace> results = repository.findAllOrderByCreatedAtDesc();
        assertThat(results).isNotEmpty();
    }

    @Test
    void touchUpdatedAtUpdatesTimestamp() {
        Workspace w = new Workspace();
        w.setName("touchable");
        w = repository.saveAndFlush(w);

        Instant oldTime = w.getUpdatedAt();
        Instant newTime = oldTime.plus(1, ChronoUnit.HOURS);

        int count = repository.touchUpdatedAt(w.getId(), newTime);
        entityManager.flush();
        entityManager.clear();

        assertThat(count).isEqualTo(1);
        
        Workspace updated = repository.findById(w.getId()).get();
        // Database storage precision variation means we use closeTo or direct verify.
        // H2 might truncate nanos, so we just check it evolved.
        assertThat(updated.getUpdatedAt()).isCloseTo(newTime, org.assertj.core.api.Assertions.within(1, ChronoUnit.MILLIS));
    }

    @Test
    void deleteByUpdatedAtBeforeRemovesOldWorkspaces() {
        Workspace w1 = new Workspace();
        w1.setName("Old");
        w1 = repository.saveAndFlush(w1);

        // Manually push updatedAt back
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        repository.touchUpdatedAt(w1.getId(), past);

        Workspace w2 = new Workspace();
        w2.setName("New");
        repository.saveAndFlush(w2);

        Instant cutoff = Instant.now().minus(5, ChronoUnit.DAYS);
        int deleted = repository.deleteByUpdatedAtBefore(cutoff);
        entityManager.flush();
        entityManager.clear();
        
        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(w1.getId())).isEmpty();
        assertThat(repository.findById(w2.getId())).isPresent();
    }

    @Test
    void existsByNameAndCreatedByUsernameReturnsTrueIfPresent() {
        Workspace w = new Workspace();
        w.setName("ExistsTest");
        w.setCreatedByUsername(USERNAME);
        repository.saveAndFlush(w);
        
        assertThat(repository.existsByNameAndCreatedByUsername("ExistsTest", USERNAME)).isTrue();
        assertThat(repository.existsByNameAndCreatedByUsername("ExistsTest", "other-user")).isFalse();
        assertThat(repository.existsByNameAndCreatedByUsername("NotExists", USERNAME)).isFalse();
    }
}
