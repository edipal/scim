package com.scimplayground.server.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark Runner for SCIM Server API.
 *
 * Run from the scim-server-api directory:
 *   mvn test-compile exec:java \
 *       -Dexec.mainClass="com.scimplayground.server.benchmark.BenchmarkRunner" \
 *       -Dexec.classpathScope=test
 *
 * Or directly:
 *   java -cp target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
 *       com.scimplayground.server.benchmark.BenchmarkRunner
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        String resultFile = args.length > 0 ? args[0] : "benchmark-results.json";

        Options opt = new OptionsBuilder()
                .include(".*Benchmark.*")
                .forks(0) // Same JVM — necessary when running without shade plugin
                .resultFormat(ResultFormatType.JSON)
                .result(resultFile)
                .shouldDoGC(true)
                .build();

        new Runner(opt).run();

        System.out.println("\n=== Benchmark results saved to: " + resultFile + " ===");
    }
}
