package de.palsoftware.scim.server.common.repository;

import de.palsoftware.scim.server.common.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByName(String name);

    Optional<Workspace> findByIdAndCreatedByUsername(UUID id, String createdByUsername);

    List<Workspace> findByCreatedByUsernameOrderByCreatedAtDesc(String createdByUsername);

    @Query("""
        SELECT w FROM Workspace w
        ORDER BY w.createdAt DESC
    """)
    List<Workspace> findAllOrderByCreatedAtDesc();

    boolean existsByName(String name);
}
