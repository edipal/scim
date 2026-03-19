package de.palsoftware.scim.server.mgmt.benchmark;

import de.palsoftware.scim.server.common.model.ScimGroup;
import de.palsoftware.scim.server.common.model.ScimGroupMembership;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for ManagementController group mapping methods.
 */
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(0)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MgmtGroupMapperBenchmark {

    private ScimGroup emptyGroup;
    private ScimGroup groupWith20Members;
    private List<ScimGroup> batchGroups;

    @Setup(Level.Trial)
    public void setup() {
        emptyGroup = buildGroup(0);
        groupWith20Members = buildGroup(20);

        batchGroups = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            batchGroups.add(buildGroup(i % 5 == 0 ? 20 : i % 3 == 0 ? 10 : 3));
        }
    }

    // ── Baseline benchmarks ──

    @Benchmark
    public Map<String, Object> mapEmptyGroup() {
        return groupToMap(emptyGroup);
    }

    @Benchmark
    public Map<String, Object> mapGroupWith20Members() {
        return groupToMap(groupWith20Members);
    }

    @Benchmark
    public void mapBatch50Groups(Blackhole bh) {
        for (ScimGroup group : batchGroups) {
            bh.consume(groupToMap(group));
        }
    }

    @Benchmark
    public Map<String, Object> mapGroupLookup() {
        return groupLookupToMap(groupWith20Members);
    }

    // ── Optimized benchmarks ──

    @Benchmark
    public Map<String, Object> mapEmptyGroupOpt() {
        return groupToMapOpt(emptyGroup);
    }

    @Benchmark
    public Map<String, Object> mapGroupWith20MembersOpt() {
        return groupToMapOpt(groupWith20Members);
    }

    @Benchmark
    public void mapBatch50GroupsOpt(Blackhole bh) {
        for (ScimGroup group : batchGroups) {
            bh.consume(groupToMapOpt(group));
        }
    }

    @Benchmark
    public Map<String, Object> mapGroupLookupOpt() {
        return groupLookupToMapOpt(groupWith20Members);
    }

    // ── Mapping methods (exact copy from ManagementController) ──

    private Map<String, Object> groupToMap(ScimGroup group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId().toString());
        map.put("displayName", group.getDisplayName());
        map.put("externalId", group.getExternalId());
        map.put("members", group.getMembers().stream()
            .map(member -> {
                Map<String, Object> memberMap = new LinkedHashMap<>();
                memberMap.put("value", member.getMemberValue() != null ? member.getMemberValue().toString() : null);
                memberMap.put("type", member.getMemberType());
                memberMap.put("display", member.getDisplay());
                return memberMap;
            })
            .toList());
        map.put("createdAt", group.getCreatedAt() != null ? group.getCreatedAt().toString() : null);
        map.put("lastModified", group.getLastModified() != null ? group.getLastModified().toString() : null);
        map.put("meta", metaToMap(group.getCreatedAt(), group.getLastModified(), group.getVersion()));
        return map;
    }

    private Map<String, Object> groupLookupToMap(ScimGroup group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId().toString());
        map.put("displayName", group.getDisplayName());
        map.put("externalId", group.getExternalId());
        return map;
    }

    private Map<String, Object> metaToMap(Instant createdAt, Instant lastModified, Long version) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("createdAt", createdAt != null ? createdAt.toString() : null);
        meta.put("lastModified", lastModified != null ? lastModified.toString() : null);
        meta.put("version", version);
        return meta;
    }

    // ── Optimized mapping methods ──

    private Map<String, Object> groupToMapOpt(ScimGroup group) {
        Map<String, Object> map = new LinkedHashMap<>(7, 1.0f);
        map.put("id", group.getId().toString());
        map.put("displayName", group.getDisplayName());
        map.put("externalId", group.getExternalId());

        List<ScimGroupMembership> members = group.getMembers();
        List<Map<String, Object>> memberList = new ArrayList<>(members.size());
        for (ScimGroupMembership member : members) {
            Map<String, Object> memberMap = new LinkedHashMap<>(3, 1.0f);
            memberMap.put("value", member.getMemberValue() != null ? member.getMemberValue().toString() : null);
            memberMap.put("type", member.getMemberType());
            memberMap.put("display", member.getDisplay());
            memberList.add(memberMap);
        }
        map.put("members", memberList);

        map.put("createdAt", group.getCreatedAt() != null ? group.getCreatedAt().toString() : null);
        map.put("lastModified", group.getLastModified() != null ? group.getLastModified().toString() : null);
        map.put("meta", metaToMapOpt(group.getCreatedAt(), group.getLastModified(), group.getVersion()));
        return map;
    }

    private Map<String, Object> groupLookupToMapOpt(ScimGroup group) {
        Map<String, Object> map = new LinkedHashMap<>(3, 1.0f);
        map.put("id", group.getId().toString());
        map.put("displayName", group.getDisplayName());
        map.put("externalId", group.getExternalId());
        return map;
    }

    private Map<String, Object> metaToMapOpt(Instant createdAt, Instant lastModified, Long version) {
        Map<String, Object> meta = new LinkedHashMap<>(3, 1.0f);
        meta.put("createdAt", createdAt != null ? createdAt.toString() : null);
        meta.put("lastModified", lastModified != null ? lastModified.toString() : null);
        meta.put("version", version);
        return meta;
    }

    // ── Fixtures ──

    private ScimGroup buildGroup(int memberCount) {
        ScimGroup group = new ScimGroup();
        group.setId(UUID.randomUUID());
        group.setDisplayName("Test Group " + memberCount);
        group.setExternalId("EXT-GRP-" + memberCount);
        group.setCreatedAt(Instant.now());
        group.setLastModified(Instant.now());
        group.setVersion((long) memberCount);

        for (int i = 0; i < memberCount; i++) {
            ScimGroupMembership m = new ScimGroupMembership();
            m.setGroup(group);
            m.setMemberValue(UUID.randomUUID());
            m.setMemberType("User");
            m.setDisplay("Member " + i);
            group.getMembers().add(m);
        }
        return group;
    }
}
