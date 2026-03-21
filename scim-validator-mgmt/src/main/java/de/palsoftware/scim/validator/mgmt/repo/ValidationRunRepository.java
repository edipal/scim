package de.palsoftware.scim.validator.mgmt.repo;

import de.palsoftware.scim.validator.mgmt.model.ValidationRun;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValidationRunRepository extends JpaRepository<ValidationRun, UUID> {
    @Query("""
        select run from ValidationRun run
        where run.createdByUser.id = :actorUserId
        """)
    List<ValidationRun> findOwnedRuns(@Param("actorUserId") String actorUserId, Sort sort);

    @Query("""
        select run from ValidationRun run
        where run.id = :id
          and run.createdByUser.id = :actorUserId
        """)
    Optional<ValidationRun> findAccessibleById(@Param("id") UUID id,
                                              @Param("actorUserId") String actorUserId);
}
