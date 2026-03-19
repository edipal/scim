package de.palsoftware.scim.validator.mgmt.repo;

import de.palsoftware.scim.validator.mgmt.model.ValidationTestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidationTestResultRepository extends JpaRepository<ValidationTestResult, UUID> {

    List<ValidationTestResult> findByRunIdOrderByStartedAtAsc(UUID runId);
}
