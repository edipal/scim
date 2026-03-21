package de.palsoftware.scim.server.mgmt.service;

import de.palsoftware.scim.server.mgmt.model.MgmtUser;
import de.palsoftware.scim.server.mgmt.repository.MgmtUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MgmtUserServiceTest {

    @Mock
    private MgmtUserRepository mgmtUserRepository;

    @InjectMocks
    private MgmtUserService service;

    // ─── provisionUser ──────────────────────────────────────────────────

    @Test
    void provisionUser_newUser_createsAndSaves() {
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.empty());
        when(mgmtUserRepository.save(any(MgmtUser.class))).thenAnswer(i -> i.getArgument(0));

        service.provisionUser("sub-123", "user@test.com");

        verify(mgmtUserRepository).save(any(MgmtUser.class));
    }

    @Test
    void provisionUser_existingUser_updatesEmailAndLogin() {
        MgmtUser existing = new MgmtUser("sub-123", "old@test.com", OffsetDateTime.now(ZoneOffset.UTC));
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.of(existing));
        when(mgmtUserRepository.save(any(MgmtUser.class))).thenAnswer(i -> i.getArgument(0));

        service.provisionUser("sub-123", "new@test.com");

        assertThat(existing.getEmail()).isEqualTo("new@test.com");
        verify(mgmtUserRepository).save(existing);
    }

    // ─── findEmailById ──────────────────────────────────────────────────

    @Test
    void findEmailById_found() {
        MgmtUser user = new MgmtUser("sub-123", "user@test.com", OffsetDateTime.now(ZoneOffset.UTC));
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.of(user));

        Optional<String> result = service.findEmailById("sub-123");

        assertThat(result).contains("user@test.com");
    }

    @Test
    void findEmailById_notFound() {
        when(mgmtUserRepository.findById("sub-999")).thenReturn(Optional.empty());

        Optional<String> result = service.findEmailById("sub-999");

        assertThat(result).isEmpty();
    }

    @Test
    void findEmailById_blankEmail_returnsEmpty() {
        MgmtUser user = new MgmtUser("sub-123", "  ", OffsetDateTime.now(ZoneOffset.UTC));
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.of(user));

        Optional<String> result = service.findEmailById("sub-123");

        assertThat(result).isEmpty();
    }

    @Test
    void findEmailById_nullEmail_returnsEmpty() {
        MgmtUser user = new MgmtUser("sub-123", null, OffsetDateTime.now(ZoneOffset.UTC));
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.of(user));

        Optional<String> result = service.findEmailById("sub-123");

        assertThat(result).isEmpty();
    }
}
