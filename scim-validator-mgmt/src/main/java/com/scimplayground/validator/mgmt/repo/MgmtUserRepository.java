package com.scimplayground.validator.mgmt.repo;

import com.scimplayground.validator.mgmt.model.MgmtUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgmtUserRepository extends JpaRepository<MgmtUser, String> {
}
