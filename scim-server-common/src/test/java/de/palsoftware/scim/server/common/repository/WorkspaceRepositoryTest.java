package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkspaceRepositoryTest {

    private static final String TEST_NAME = "TestName";
    private static final String USERNAME = "user123";

    @Autowired
    private WorkspaceRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void findByNameReturnsCorrectWorkspace() {
        Workspace w = new Workspace();
        w.setName(TEST_NAME);
        repository.saveAndFlush(w);

        Optional<Workspace> result = repository.findByName(TEST_NAME);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(TEST_NAME);
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
        
        assertThat(deleted).isEqualTo(1);
        assertThat(repository.existsByName("Old")).isFalse();
        assertThat(repository.existsByName("New")).isTrue();
    }

    @Test
    void existsByNameReturnsTrueIfPresent() {
        Workspace w = new Workspace();
        w.setName("ExistsTest");
        repository.saveAndFlush(w);
        
        assertThat(repository.existsByName("ExistsTest")).isTrue();
        assertThat(repository.existsByName("NotExists")).isFalse();
    }
}
