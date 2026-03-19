package de.palsoftware.scim.server.mgmt.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark Runner for SCIM Server Management module.
 *
 * Run from the scim-server-mgmt directory:
 *   mvn test-compile exec:java -Dexec.arguments="benchmark-reports/results.json" -q
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        String resultFile = args.length > 0 ? args[0] : "benchmark-results.json";

        Options opt = new OptionsBuilder()
                .include(".*Benchmark.*")
                .forks(0)
                .resultFormat(ResultFormatType.JSON)
                .result(resultFile)
                .shouldDoGC(true)
                .build();

        new Runner(opt).run();

        System.out.println("\n=== Benchmark results saved to: " + resultFile + " ===");
    }
}
