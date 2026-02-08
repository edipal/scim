package com.scimplayground.validator.mgmt.service;

import com.scimplayground.validator.mgmt.model.MgmtUser;
import com.scimplayground.validator.mgmt.repo.MgmtUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
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
}
