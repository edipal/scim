package com.scimplayground.mgmt.service;

import com.scimplayground.server.model.Workspace;
import com.scimplayground.server.model.WorkspaceToken;
import com.scimplayground.server.repository.WorkspaceDataStats;
import com.scimplayground.server.repository.WorkspaceRepository;
import com.scimplayground.server.repository.WorkspaceStatsRepository;
import com.scimplayground.server.repository.WorkspaceTokenRepository;
import com.scimplayground.server.security.TokenSecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceTokenRepository tokenRepository;
    private final WorkspaceStatsRepository workspaceStatsRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                             WorkspaceTokenRepository tokenRepository,
                             WorkspaceStatsRepository workspaceStatsRepository) {
        this.workspaceRepository = workspaceRepository;
        this.tokenRepository = tokenRepository;
        this.workspaceStatsRepository = workspaceStatsRepository    ;
    }

    public Workspace createWorkspace(String name, String description, String createdByUsername) {
        Workspace ws = new Workspace();
        ws.setName(name);
        ws.setDescription(description);
        ws.setCreatedByUsername(createdByUsername);
        return workspaceRepository.save(ws);
    }

    public List<Workspace> listWorkspaces(String actorUsername, boolean admin) {
        if (admin) {
            return workspaceRepository.findAllOrderByCreatedAtDesc();
        }
        return workspaceRepository.findByCreatedByUsernameOrderByCreatedAtDesc(actorUsername);
    }

    public Optional<Workspace> getWorkspace(UUID id, String actorUsername, boolean admin) {
        if (admin) {
            return workspaceRepository.findById(id);
        }
        return workspaceRepository.findByIdAndCreatedByUsername(id, actorUsername);
    }

    public Optional<Workspace> getWorkspaceByName(String name) {
        return workspaceRepository.findByName(name);
    }

    public Workspace requireWorkspaceAccess(UUID id, String actorUsername, boolean admin) {
        return getWorkspace(id, actorUsername, admin)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    }

    @Transactional(readOnly = true)
    public WorkspaceDataStats getWorkspaceDataStats(UUID workspaceId, String actorUsername, boolean admin) {
        requireWorkspaceAccess(workspaceId, actorUsername, admin);
        return workspaceStatsRepository.fetchWorkspaceDataStats(workspaceId);
    }

    public void deleteWorkspace(UUID id, String actorUsername, boolean admin) {
        Workspace workspace = requireWorkspaceAccess(id, actorUsername, admin);
        workspaceRepository.delete(workspace);
    }

    /**
     * Generate a new bearer token for a workspace.
     * Returns the raw token value (shown once to the user).
     */
    public String generateToken(UUID workspaceId, String name, String description, String actorUsername, boolean admin) {
        Workspace ws = requireWorkspaceAccess(workspaceId, actorUsername, admin);

        String rawToken = generateSecureToken();
        String tokenHash = TokenSecurityUtil.sha256Hex(rawToken);

        WorkspaceToken token = new WorkspaceToken();
        token.setWorkspace(ws);
        token.setTokenHash(tokenHash);
        token.setName(name);
        token.setDescription(description);
        tokenRepository.save(token);

        return rawToken;
    }

    public List<WorkspaceToken> listTokens(UUID workspaceId, String actorUsername, boolean admin) {
        requireWorkspaceAccess(workspaceId, actorUsername, admin);
        return tokenRepository.findByWorkspaceId(workspaceId);
    }

    public void revokeToken(UUID workspaceId, UUID tokenId, String actorUsername, boolean admin) {
        requireWorkspaceAccess(workspaceId, actorUsername, admin);
        WorkspaceToken token = tokenRepository.findByIdAndWorkspaceId(tokenId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found"));
        token.setRevoked(true);
        tokenRepository.save(token);
    }

    private String generateSecureToken() {
        return TokenSecurityUtil.generateUrlSafeToken(48);
    }
}
