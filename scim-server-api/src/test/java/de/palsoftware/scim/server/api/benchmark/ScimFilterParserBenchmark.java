package de.palsoftware.scim.server.api.benchmark;

import de.palsoftware.scim.server.api.scim.filter.ScimFilterParser;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(0)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ScimFilterParserBenchmark {

    private UUID workspaceId;

    @Setup(Level.Trial)
    public void setup() {
        workspaceId = UUID.randomUUID();
    }

    // ── Simple filters ──

    @Benchmark
    public void parseSimpleEq(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter("userName eq \"john.doe\"", workspaceId));
    }

    @Benchmark
    public void parseSimpleCo(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter("displayName co \"John\"", workspaceId));
    }

    @Benchmark
    public void parseSimpleSw(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter("userName sw \"john\"", workspaceId));
    }

    @Benchmark
    public void parsePresence(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter("externalId pr", workspaceId));
    }

    // ── Compound filters ──

    @Benchmark
    public void parseAndFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "userName eq \"john.doe\" and active eq true", workspaceId));
    }

    @Benchmark
    public void parseOrFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "displayName co \"John\" or userName sw \"john\"", workspaceId));
    }

    @Benchmark
    public void parseComplexFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "(displayName co \"John\" or userName sw \"j\") and active eq true", workspaceId));
    }

    @Benchmark
    public void parseNotFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "not (userName eq \"admin\")", workspaceId));
    }

    // ── Enterprise extension filter ──

    @Benchmark
    public void parseEnterpriseFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department eq \"Engineering\"",
            workspaceId));
    }

    // ── Name sub-attribute filter ──

    @Benchmark
    public void parseNameSubFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "name.familyName eq \"Doe\" and name.givenName eq \"John\"", workspaceId));
    }

    // ── Group filter ──

    @Benchmark
    public void parseGroupFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseGroupFilter(
            "displayName eq \"Engineering\"", workspaceId));
    }

    // ── Deeply nested filter ──

    @Benchmark
    public void parseDeeplyNestedFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(
            "((userName eq \"a\" or userName eq \"b\") and (active eq true or title pr)) or displayName co \"test\"",
            workspaceId));
    }

    // ── No filter (empty/null) ──

    @Benchmark
    public void parseNullFilter(Blackhole bh) {
        bh.consume(ScimFilterParser.parseUserFilter(null, workspaceId));
    }

    // ── Sort attribute resolution ──

    @Benchmark
    public void resolveUserSortAttribute(Blackhole bh) {
        bh.consume(ScimFilterParser.resolveUserSortAttribute("name.familyName"));
    }

    @Benchmark
    public void resolveGroupSortAttribute(Blackhole bh) {
        bh.consume(ScimFilterParser.resolveGroupSortAttribute("displayName"));
    }
}
