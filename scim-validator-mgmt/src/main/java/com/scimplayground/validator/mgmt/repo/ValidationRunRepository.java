package com.scimplayground.validator.mgmt.repo;

import com.scimplayground.validator.mgmt.model.ValidationRun;
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
           or (run.createdByUser is null and run.createdByUsername = :actorUsername)
        """)
    List<ValidationRun> findOwnedRuns(@Param("actorUserId") String actorUserId,
                                     @Param("actorUsername") String actorUsername,
                                     Sort sort);

    @Query("""
        select run from ValidationRun run
        where run.id = :id
          and (
                run.createdByUser.id = :actorUserId
             or (run.createdByUser is null and run.createdByUsername = :actorUsername)
          )
        """)
    Optional<ValidationRun> findAccessibleById(@Param("id") UUID id,
                                              @Param("actorUserId") String actorUserId,
                                              @Param("actorUsername") String actorUsername);
}
