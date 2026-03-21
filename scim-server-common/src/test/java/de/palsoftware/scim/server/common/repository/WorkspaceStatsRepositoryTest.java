package de.palsoftware.scim.server.common.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceStatsRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private WorkspaceStatsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new WorkspaceStatsRepository(entityManager);
    }

    @Test
    void fetchWorkspaceDataStats_mapsArrayToDtoCorrectly() {
        UUID workspaceId = UUID.randomUUID();

        // 14 elements returned by the custom native query
        Object[] fakeResult = new Object[]{
            1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 1024L
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("workspaceId", workspaceId)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(fakeResult);

        WorkspaceDataStats stats = repository.fetchWorkspaceDataStats(workspaceId);

        verify(entityManager).createNativeQuery(anyString());
        verify(query).setParameter("workspaceId", workspaceId);
        verify(query).getSingleResult();

        assertThat(stats.userCount()).isEqualTo(1L);
        assertThat(stats.groupCount()).isEqualTo(2L);
        assertThat(stats.tokenCount()).isEqualTo(3L);
        assertThat(stats.logCount()).isEqualTo(4L);
        assertThat(stats.emailCount()).isEqualTo(5L);
        assertThat(stats.phoneNumberCount()).isEqualTo(6L);
        assertThat(stats.addressCount()).isEqualTo(7L);
        assertThat(stats.entitlementCount()).isEqualTo(8L);
        assertThat(stats.roleCount()).isEqualTo(9L);
        assertThat(stats.imCount()).isEqualTo(10L);
        assertThat(stats.photoCount()).isEqualTo(11L);
        assertThat(stats.x509CertificateCount()).isEqualTo(12L);
        assertThat(stats.groupMembershipCount()).isEqualTo(13L);
        assertThat(stats.estimatedRowBytes()).isEqualTo(1024L);
    }

    @Test
    void fetchWorkspaceDataStats_handlesNullValuesGracefully() {
        UUID workspaceId = UUID.randomUUID();

        // Handling nulls natively returned by JPA
        Object[] fakeResult = new Object[14];
        fakeResult[0] = 5L;
        // Remaining are null

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("workspaceId", workspaceId)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(fakeResult);

        WorkspaceDataStats stats = repository.fetchWorkspaceDataStats(workspaceId);

        assertThat(stats.userCount()).isEqualTo(5L);
        assertThat(stats.groupCount()).isZero(); // Defaults to 0
        assertThat(stats.estimatedRowBytes()).isZero();
    }
}
