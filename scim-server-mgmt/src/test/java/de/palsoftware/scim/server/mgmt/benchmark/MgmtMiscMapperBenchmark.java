package de.palsoftware.scim.server.mgmt.benchmark;

import de.palsoftware.scim.server.common.model.ScimRequestLog;
import de.palsoftware.scim.server.common.model.Workspace;
import de.palsoftware.scim.server.common.model.WorkspaceToken;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for ManagementController log/token/workspace mapping methods.
 */
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(0)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MgmtMiscMapperBenchmark {

    private ScimRequestLog log;
    private WorkspaceToken token;
    private Workspace workspace;
    private List<ScimRequestLog> batchLogs;

    @Setup(Level.Trial)
    public void setup() {
        log = buildLog();
        token = buildToken();
        workspace = buildWorkspace();

        batchLogs = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            batchLogs.add(buildLog());
        }
    }

    // ── Baseline benchmarks ──

    @Benchmark
    public Map<String, Object> mapLog() {
        return logToMap(log);
    }

    @Benchmark
    public void mapBatch50Logs(Blackhole bh) {
        for (ScimRequestLog l : batchLogs) {
            bh.consume(logToMap(l));
        }
    }

    @Benchmark
    public Map<String, Object> mapToken() {
        return tokenToMap(token);
    }

    @Benchmark
    public Map<String, Object> mapWorkspace() {
        return buildWorkspaceMap(workspace, "owner@example.com");
    }

    // ── Optimized benchmarks ──

    @Benchmark
    public Map<String, Object> mapLogOpt() {
        return logToMapOpt(log);
    }

    @Benchmark
    public void mapBatch50LogsOpt(Blackhole bh) {
        for (ScimRequestLog l : batchLogs) {
            bh.consume(logToMapOpt(l));
        }
    }

    @Benchmark
    public Map<String, Object> mapTokenOpt() {
        return tokenToMapOpt(token);
    }

    @Benchmark
    public Map<String, Object> mapWorkspaceOpt() {
        return buildWorkspaceMapOpt(workspace, "owner@example.com");
    }

    // ── Mapping methods (exact copy from ManagementController) ──

    private Map<String, Object> logToMap(ScimRequestLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId() != null ? log.getId().toString() : null);
        map.put("workspaceId", log.getWorkspace() != null && log.getWorkspace().getId() != null
            ? log.getWorkspace().getId().toString()
            : null);
        map.put("method", log.getMethod());
        map.put("path", log.getPath());
        map.put("status", log.getStatus());
        map.put("requestBody", log.getRequestBody());
        map.put("responseBody", log.getResponseBody());
        map.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> tokenToMap(WorkspaceToken token) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", token.getId().toString());
        map.put("name", token.getName());
        map.put("description", token.getDescription());
        map.put("revoked", token.isRevoked());
        map.put("createdAt", token.getCreatedAt() != null ? token.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> buildWorkspaceMap(Workspace ws, String ownerDisplayName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", ws.getId().toString());
        map.put("name", ws.getName());
        map.put("description", ws.getDescription());
        map.put("createdByUsername", ws.getCreatedByUsername());
        map.put("createdByDisplayName", ownerDisplayName);
        map.put("createdAt", ws.getCreatedAt() != null ? ws.getCreatedAt().toString() : null);
        map.put("updatedAt", ws.getUpdatedAt() != null ? ws.getUpdatedAt().toString() : null);
        return map;
    }

    // ── Optimized mapping methods ──

    private Map<String, Object> logToMapOpt(ScimRequestLog log) {
        Map<String, Object> map = new LinkedHashMap<>(8, 1.0f);
        map.put("id", log.getId() != null ? log.getId().toString() : null);
        map.put("workspaceId", log.getWorkspace() != null && log.getWorkspace().getId() != null
            ? log.getWorkspace().getId().toString()
            : null);
        map.put("method", log.getMethod());
        map.put("path", log.getPath());
        map.put("status", log.getStatus());
        map.put("requestBody", log.getRequestBody());
        map.put("responseBody", log.getResponseBody());
        map.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> tokenToMapOpt(WorkspaceToken token) {
        Map<String, Object> map = new LinkedHashMap<>(5, 1.0f);
        map.put("id", token.getId().toString());
        map.put("name", token.getName());
        map.put("description", token.getDescription());
        map.put("revoked", token.isRevoked());
        map.put("createdAt", token.getCreatedAt() != null ? token.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> buildWorkspaceMapOpt(Workspace ws, String ownerDisplayName) {
        Map<String, Object> map = new LinkedHashMap<>(7, 1.0f);
        map.put("id", ws.getId().toString());
        map.put("name", ws.getName());
        map.put("description", ws.getDescription());
        map.put("createdByUsername", ws.getCreatedByUsername());
        map.put("createdByDisplayName", ownerDisplayName);
        map.put("createdAt", ws.getCreatedAt() != null ? ws.getCreatedAt().toString() : null);
        map.put("updatedAt", ws.getUpdatedAt() != null ? ws.getUpdatedAt().toString() : null);
        return map;
    }

    // ── Fixtures ──

    private ScimRequestLog buildLog() {
        ScimRequestLog requestLog = new ScimRequestLog();
        requestLog.setId(UUID.randomUUID());
        Workspace ws = new Workspace();
        ws.setId(UUID.randomUUID());
        requestLog.setWorkspace(ws);
        requestLog.setMethod("POST");
        requestLog.setPath("/ws/test/scim/v2/Users");
        requestLog.setStatus(201);
        requestLog.setRequestBody("{\"userName\":\"test\"}");
        requestLog.setResponseBody("{\"id\":\"abc\",\"userName\":\"test\"}");
        requestLog.setCreatedAt(Instant.now());
        return requestLog;
    }

    private WorkspaceToken buildToken() {
        WorkspaceToken workspaceToken = new WorkspaceToken();
        workspaceToken.setId(UUID.randomUUID());
        workspaceToken.setName("Test Token");
        workspaceToken.setDescription("A test token for benchmarking");
        workspaceToken.setRevoked(false);
        workspaceToken.setCreatedAt(Instant.now());
        return workspaceToken;
    }

    private Workspace buildWorkspace() {
        Workspace ws = new Workspace();
        ws.setId(UUID.randomUUID());
        ws.setName("benchmark-workspace");
        ws.setDescription("Workspace for benchmarking");
        ws.setCreatedByUsername("admin");
        ws.setCreatedAt(Instant.now());
        ws.setUpdatedAt(Instant.now());
        return ws;
    }
}
