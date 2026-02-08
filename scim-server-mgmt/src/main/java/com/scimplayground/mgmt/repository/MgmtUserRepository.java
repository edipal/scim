package com.scimplayground.mgmt.repository;

import com.scimplayground.mgmt.model.MgmtUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgmtUserRepository extends JpaRepository<MgmtUser, String> {
}
