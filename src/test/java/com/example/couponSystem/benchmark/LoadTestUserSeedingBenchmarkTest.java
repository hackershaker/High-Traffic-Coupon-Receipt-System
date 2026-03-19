package com.example.couponSystem.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.example.couponSystem.config.TestDataProperties;
import com.example.couponSystem.config.TestDataSeedMode;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.UserRepository;
import com.example.couponSystem.security.LoadTestUserSeedResult;
import com.example.couponSystem.security.LoadTestUserSeedingService;

@Tag("benchmark")
class LoadTestUserSeedingBenchmarkTest {

    private static final int WARMUP_RUNS = 1;
    private static final int MEASURED_RUNS = 5;

    @Test
    void compareBaselineAndOptimized_withMockedRepository() {
        MockScenario scenario = new MockScenario("mock-10000-users", "load-test-user-", 10000, 4000, 500);

        ModeBenchmarkResult baseline = runMode(scenario, TestDataSeedMode.BASELINE);
        ModeBenchmarkResult optimized = runMode(scenario, TestDataSeedMode.OPTIMIZED);

        long expectedInserted = scenario.userCount() - scenario.existingUserCount();
        assertThat(baseline.insertedMedian()).isEqualTo(expectedInserted);
        assertThat(optimized.insertedMedian()).isEqualTo(expectedInserted);
        assertThat(baseline.lookupCallMedian()).isEqualTo(scenario.userCount());
        assertThat(optimized.lookupCallMedian()).isEqualTo(1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenario", scenario.toMap());
        payload.put("baseline", baseline.toMap());
        payload.put("optimized", optimized.toMap());

        String markdown = """
                # Mock Seeding Benchmark

                | mode | total median (ms) | total p95 (ms) | lookup median (ms) | insert median (ms) | lookup calls median |
                | --- | ---: | ---: | ---: | ---: | ---: |
                | baseline | %d | %d | %d | %d | %d |
                | optimized | %d | %d | %d | %d | %d |
                """.formatted(
                baseline.totalMedian(),
                baseline.totalP95(),
                baseline.lookupMedian(),
                baseline.insertMedian(),
                baseline.lookupCallMedian(),
                optimized.totalMedian(),
                optimized.totalP95(),
                optimized.lookupMedian(),
                optimized.insertMedian(),
                optimized.lookupCallMedian());

        BenchmarkReportWriter.write("mock-benchmark", payload, markdown);
    }

    @Test
    void bothModes_areIdempotent_whenRunTwiceOnSameDataset() {
        MockScenario scenario = new MockScenario("idempotency", "seed-user-", 1000, 500, 200);

        assertIdempotent(scenario, TestDataSeedMode.BASELINE);
        assertIdempotent(scenario, TestDataSeedMode.OPTIMIZED);
    }

    private void assertIdempotent(MockScenario scenario, TestDataSeedMode mode) {
        UserRepository repository = mock(UserRepository.class);
        SeedFixture fixture = new SeedFixture(scenario, repository);
        LoadTestUserSeedingService service = new LoadTestUserSeedingService(repository);

        LoadTestUserSeedResult first = service.seedUsers(fixture.properties(mode), "pw-hash");
        LoadTestUserSeedResult second = service.seedUsers(fixture.properties(mode), "pw-hash");

        long expectedInserted = scenario.userCount() - scenario.existingUserCount();
        assertThat(first.insertedUserCount()).isEqualTo(expectedInserted);
        assertThat(second.insertedUserCount()).isEqualTo(0);
    }

    private ModeBenchmarkResult runMode(MockScenario scenario, TestDataSeedMode mode) {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            runSingleIteration(scenario, mode);
        }

        List<LoadTestUserSeedResult> measured = new ArrayList<>();
        for (int i = 0; i < MEASURED_RUNS; i++) {
            measured.add(runSingleIteration(scenario, mode));
        }

        return new ModeBenchmarkResult(mode, measured);
    }

    /**
     * Runs one benchmark iteration for the given seed mode with a fresh mocked repository.
     * Validates the expected repository access pattern for each mode and returns timing/count
     * metrics captured by the seeding service.
     *
     * @param scenario benchmark input size/profile
     * @param mode baseline or optimized seeding strategy
     * @return per-iteration seeding metrics used for aggregate benchmark statistics
     */
    private LoadTestUserSeedResult runSingleIteration(MockScenario scenario, TestDataSeedMode mode) {
        UserRepository repository = mock(UserRepository.class);
        SeedFixture fixture = new SeedFixture(scenario, repository);
        LoadTestUserSeedingService service = new LoadTestUserSeedingService(repository);

        LoadTestUserSeedResult result = service.seedUsers(fixture.properties(mode), "pw-hash");

        if (mode == TestDataSeedMode.BASELINE) {
            verify(repository, times(scenario.userCount())).existsByUsername(anyString());
            verify(repository, never()).findUsernamesByPrefix(anyString());
        } else {
            verify(repository, never()).existsByUsername(anyString());
            verify(repository, times(1)).findUsernamesByPrefix(scenario.prefix());
        }

        return result;
    }

    private record MockScenario(String name, String prefix, int userCount, int existingUserCount, int batchSize) {
        Map<String, Object> toMap() {
            return Map.of(
                    "name", name,
                    "prefix", prefix,
                    "userCount", userCount,
                    "existingUserCount", existingUserCount,
                    "batchSize", batchSize);
        }
    }

    /**
     * Test fixture that prepares a mocked {@link UserRepository} to behave like a
     * pre-populated user store for the benchmark scenario.
     *
     * <p>It tracks an in-memory set of existing usernames and wires Mockito
     * stubs for lookup/insert methods so each iteration can run deterministically
     * without a real database.</p>
     */
    private static class SeedFixture {
        private final MockScenario scenario;
        private final Set<String> existingUsers;

        /**
         * Initializes repository stubs and in-memory user state for one scenario.
         *
         * @param scenario benchmark scenario metadata
         * @param repository mocked repository used by the seeding service
         */
        SeedFixture(MockScenario scenario, UserRepository repository) {
            this.scenario = scenario;
            this.existingUsers = IntStream.rangeClosed(1, scenario.existingUserCount())
                    .mapToObj(index -> scenario.prefix() + index)
                    .collect(Collectors.toCollection(HashSet::new));

            when(repository.existsByUsername(anyString()))
                    .thenAnswer(invocation -> existingUsers.contains(invocation.getArgument(0)));

            when(repository.findUsernamesByPrefix(anyString()))
                    .thenAnswer(invocation -> new ArrayList<>(existingUsers));

            when(repository.save(any(Member.class))).thenAnswer(invocation -> {
                Member member = invocation.getArgument(0);
                existingUsers.add(member.getUsername());
                return member;
            });

            when(repository.saveAll(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<Member> members = (List<Member>) invocation.getArgument(0);
                members.forEach(member -> existingUsers.add(member.getUsername()));
                return members;
            });
        }

        /**
         * Builds seeding properties for the current scenario and target mode.
         *
         * @param mode baseline or optimized seeding strategy
         * @return configured test-data properties used by the seeding service
         */
        TestDataProperties properties(TestDataSeedMode mode) {
            TestDataProperties properties = new TestDataProperties();
            properties.setUserPrefix(scenario.prefix());
            properties.setUserCount(scenario.userCount());
            properties.setUserInsertBatchSize(scenario.batchSize());
            properties.setSeedMode(mode);
            return properties;
        }
    }

    private record ModeBenchmarkResult(TestDataSeedMode mode, List<LoadTestUserSeedResult> samples) {
        long totalMedian() {
            return BenchmarkStats.median(samples.stream().map(LoadTestUserSeedResult::totalMillis).toList());
        }

        long totalP95() {
            return BenchmarkStats.p95(samples.stream().map(LoadTestUserSeedResult::totalMillis).toList());
        }

        long lookupMedian() {
            return BenchmarkStats.median(samples.stream().map(LoadTestUserSeedResult::existingLookupMillis).toList());
        }

        long insertMedian() {
            return BenchmarkStats.median(samples.stream().map(LoadTestUserSeedResult::insertMillis).toList());
        }

        long insertedMedian() {
            return BenchmarkStats.median(samples.stream().map(LoadTestUserSeedResult::insertedUserCount).toList());
        }

        long lookupCallMedian() {
            return BenchmarkStats.median(samples.stream().map(LoadTestUserSeedResult::existingLookupCallCount).toList());
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "mode", mode.name(),
                    "totalMillisMedian", totalMedian(),
                    "totalMillisP95", totalP95(),
                    "lookupMillisMedian", lookupMedian(),
                    "insertMillisMedian", insertMedian(),
                    "lookupCallMedian", lookupCallMedian(),
                    "insertedMedian", insertedMedian());
        }
    }
}




