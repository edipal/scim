package de.palsoftware.scim.validator.mgmt.repo;

import de.palsoftware.scim.validator.mgmt.model.ValidationMgmtUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationMgmtUserRepository extends JpaRepository<ValidationMgmtUser, String> {
}
