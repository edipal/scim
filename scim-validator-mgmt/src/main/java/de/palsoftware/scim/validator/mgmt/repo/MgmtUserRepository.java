package de.palsoftware.scim.validator.mgmt.repo;

import de.palsoftware.scim.validator.mgmt.model.MgmtUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgmtUserRepository extends JpaRepository<MgmtUser, String> {
}
