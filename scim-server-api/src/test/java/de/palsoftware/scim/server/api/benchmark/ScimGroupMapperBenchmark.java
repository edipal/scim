package de.palsoftware.scim.server.api.benchmark;

import de.palsoftware.scim.server.common.model.ScimGroup;
import de.palsoftware.scim.server.common.model.ScimGroupMembership;
import de.palsoftware.scim.server.api.scim.mapper.ScimGroupMapper;
import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(0)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ScimGroupMapperBenchmark {

    private ScimGroup emptyGroup;
    private ScimGroup groupWithMembers;
    private String baseUrl;
    private List<ScimGroup> batchGroups;

    @Setup(Level.Trial)
    public void setup() {
        baseUrl = "https://scim.example.com/ws/test/scim/v2";
        emptyGroup = buildEmptyGroup();
        groupWithMembers = buildGroupWithMembers(20);

        batchGroups = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            batchGroups.add(buildGroupWithMembers(5));
        }
    }

    @Benchmark
    public Map<String, Object> mapEmptyGroup() {
        return ScimGroupMapper.toScimResponse(emptyGroup, baseUrl);
    }

    @Benchmark
    public Map<String, Object> mapGroupWith20Members() {
        return ScimGroupMapper.toScimResponse(groupWithMembers, baseUrl);
    }

    @Benchmark
    public List<Map<String, Object>> mapBatch50Groups() {
        List<Map<String, Object>> result = new ArrayList<>(50);
        for (ScimGroup group : batchGroups) {
            result.add(ScimGroupMapper.toScimResponse(group, baseUrl));
        }
        return result;
    }

    private ScimGroup buildEmptyGroup() {
        ScimGroup group = new ScimGroup();
        group.setId(UUID.randomUUID());
        group.setDisplayName("Empty Group");
        group.setExternalId("ext-group-1");
        group.setCreatedAt(Instant.parse("2024-01-15T10:30:00Z"));
        group.setLastModified(Instant.parse("2024-06-15T14:45:00Z"));
        group.setVersion(1L);
        return group;
    }

    private ScimGroup buildGroupWithMembers(int memberCount) {
        ScimGroup group = new ScimGroup();
        group.setId(UUID.randomUUID());
        group.setDisplayName("Engineering Team");
        group.setExternalId("ext-eng-team");
        group.setCreatedAt(Instant.parse("2024-01-15T10:30:00Z"));
        group.setLastModified(Instant.parse("2024-06-15T14:45:00Z"));
        group.setVersion(3L);

        for (int i = 0; i < memberCount; i++) {
            ScimGroupMembership m = new ScimGroupMembership();
            m.setId(UUID.randomUUID());
            m.setGroup(group);
            m.setMemberValue(UUID.randomUUID());
            m.setMemberType("User");
            m.setDisplay("User " + i);
            group.getMembers().add(m);
        }
        return group;
    }
}
