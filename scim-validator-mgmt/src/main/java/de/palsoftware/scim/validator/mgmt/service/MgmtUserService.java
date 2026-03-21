package de.palsoftware.scim.validator.mgmt.service;

import de.palsoftware.scim.validator.mgmt.model.ValidationMgmtUser;
import de.palsoftware.scim.validator.mgmt.repo.ValidationMgmtUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class MgmtUserService {

    private final ValidationMgmtUserRepository mgmtUserRepository;

    public MgmtUserService(ValidationMgmtUserRepository mgmtUserRepository) {
        this.mgmtUserRepository = mgmtUserRepository;
    }

    @Transactional
    public void provisionUser(String sub, String email) {
        ValidationMgmtUser user = mgmtUserRepository.findById(sub)
                .orElseGet(() -> new ValidationMgmtUser(sub, email, OffsetDateTime.now(ZoneOffset.UTC)));
        user.setEmail(email);
        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        mgmtUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String resolveDisplayName(String sub, String fallbackDisplayName) {
        if (sub == null || sub.isBlank()) {
            return fallbackDisplayName;
        }
        return mgmtUserRepository.findById(sub)
                .map(ValidationMgmtUser::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .orElse(fallbackDisplayName);
    }
}
