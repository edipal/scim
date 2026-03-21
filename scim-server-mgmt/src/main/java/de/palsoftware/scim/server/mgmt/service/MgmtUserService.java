package de.palsoftware.scim.server.mgmt.service;

import de.palsoftware.scim.server.mgmt.model.MgmtUser;
import de.palsoftware.scim.server.mgmt.repository.MgmtUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MgmtUserService {

    private final MgmtUserRepository mgmtUserRepository;

    public MgmtUserService(MgmtUserRepository mgmtUserRepository) {
        this.mgmtUserRepository = mgmtUserRepository;
    }

    @Transactional
    public void provisionUser(String sub, String email) {
        MgmtUser user = mgmtUserRepository.findById(sub)
                .orElseGet(() -> new MgmtUser(sub, email, OffsetDateTime.now(ZoneOffset.UTC)));
        user.setEmail(email);
        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        mgmtUserRepository.save(user);
    }

    public Optional<String> findEmailById(String sub) {
        return mgmtUserRepository.findById(sub)
                .map(MgmtUser::getEmail)
                .filter(e -> e != null && !e.isBlank());
    }
}

