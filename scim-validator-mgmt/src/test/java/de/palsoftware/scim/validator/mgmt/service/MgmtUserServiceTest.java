package de.palsoftware.scim.validator.mgmt.service;

import de.palsoftware.scim.validator.mgmt.model.ValidationMgmtUser;
import de.palsoftware.scim.validator.mgmt.repo.ValidationMgmtUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MgmtUserServiceTest {

    @Mock
    private ValidationMgmtUserRepository mgmtUserRepository;

    @InjectMocks
    private MgmtUserService mgmtUserService;

    @Test
    void provisionUser_newUser_createsWithEmailAndTimestamp() {
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.empty());
        when(mgmtUserRepository.save(any(ValidationMgmtUser.class))).thenAnswer(i -> i.getArgument(0));

        mgmtUserService.provisionUser("sub-123", "user@example.com");

        verify(mgmtUserRepository).save(argThat(user -> "sub-123".equals(user.getId()) &&
                "user@example.com".equals(user.getEmail()) &&
                user.getLastLoginAt() != null));
    }

    @Test
    void provisionUser_existingUser_updatesEmailAndLoginTime() {
        ValidationMgmtUser existing = new ValidationMgmtUser("sub-123", "old@example.com",
                OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.of(existing));
        when(mgmtUserRepository.save(any(ValidationMgmtUser.class))).thenAnswer(i -> i.getArgument(0));

        mgmtUserService.provisionUser("sub-123", "new@example.com");

        verify(mgmtUserRepository).save(argThat(user -> "new@example.com".equals(user.getEmail()) &&
                user.getLastLoginAt().isAfter(
                        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))));
    }

    @Test
    void resolveDisplayName_nullSub_returnsFallback() {
        String result = mgmtUserService.resolveDisplayName(null, "fallback-name");

        assertThat(result).isEqualTo("fallback-name");
    }

    @Test
    void resolveDisplayName_blankSub_returnsFallback() {
        String result = mgmtUserService.resolveDisplayName("   ", "fallback-name");

        assertThat(result).isEqualTo("fallback-name");
    }

    @Test
    void resolveDisplayName_userNotFound_returnsFallback() {
        when(mgmtUserRepository.findById("sub-404")).thenReturn(Optional.empty());

        String result = mgmtUserService.resolveDisplayName("sub-404", "fallback-name");

        assertThat(result).isEqualTo("fallback-name");
    }

    @ParameterizedTest
    @CsvSource({
            "user@example.com, user@example.com",
            "'   ', fallback-name",
            ", fallback-name"
    })
    void resolveDisplayName_userWithEmail_returnsExpected(String email, String expected) {
        ValidationMgmtUser user = new ValidationMgmtUser("sub-123", email,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(mgmtUserRepository.findById("sub-123")).thenReturn(Optional.of(user));

        String result = mgmtUserService.resolveDisplayName("sub-123", "fallback-name");

        assertThat(result).isEqualTo(expected);
    }
}
