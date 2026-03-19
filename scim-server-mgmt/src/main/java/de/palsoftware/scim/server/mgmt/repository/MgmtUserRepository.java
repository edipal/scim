package de.palsoftware.scim.server.mgmt.repository;

import de.palsoftware.scim.server.mgmt.model.MgmtUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgmtUserRepository extends JpaRepository<MgmtUser, String> {
}
