package com.example.couponSystem.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.couponSystem.CouponSystemApplication;
import com.example.couponSystem.config.TestDataProperties;
import com.example.couponSystem.config.TestDataSeedMode;
import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;
import com.example.couponSystem.security.TestDataSeedingMetrics;
import com.example.couponSystem.security.TestDataSeedingMetricsRecorder;

@Tag("benchmark")
@Tag("docker")
@Tag("benchmark-docker")
@Testcontainers
class StartupBenchmarkDockerTest {

    private static final int WARMUP_RUNS = 1;
    private static final int MEASURED_RUNS = 5;

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.45"))
            .withDatabaseName("coupon_benchmark")
            .withUsername("test")
            .withPassword("test");

    @Test
    void compareStartupTime_baselineVsOptimized_onMysqlContainer() {
        TestDataProperties defaults = new TestDataProperties();
        List<StartupScenario> scenarios = List.of(
                new StartupScenario("small", defaults.getBenchmarkSmallCouponCount(), defaults.getBenchmarkSmallUserCount()),
                new StartupScenario("large", defaults.getBenchmarkLargeCouponCount(), defaults.getBenchmarkLargeUserCount()));

        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> markdownRows = new ArrayList<>();

        for (StartupScenario scenario : scenarios) {
            ModeStartupResult baseline = runModeForScenario(scenario, TestDataSeedMode.BASELINE);
            ModeStartupResult optimized = runModeForScenario(scenario, TestDataSeedMode.OPTIMIZED);

            assertThat(baseline.insertedUsersMedian()).isEqualTo(scenario.userCount());
            assertThat(optimized.insertedUsersMedian()).isEqualTo(scenario.userCount());
            assertThat(baseline.assignedCouponSeedTarget()).isEqualTo(scenario.couponCount());
            assertThat(optimized.assignedCouponSeedTarget()).isEqualTo(scenario.couponCount());

            payload.put(scenario.name() + "-baseline", baseline.toMap());
            payload.put(scenario.name() + "-optimized", optimized.toMap());

            markdownRows.add("| %s | baseline | %d | %d | %d | %d | %d | %d | %d |".formatted(
                    scenario.name(),
                    baseline.startupMedian(),
                    baseline.startupP95(),
                    baseline.couponSeedMedian(),
                    baseline.userSeedMedian(),
                    baseline.lookupMedian(),
                    baseline.insertMedian(),
                    baseline.lookupCallMedian()));
            markdownRows.add("| %s | optimized | %d | %d | %d | %d | %d | %d | %d |".formatted(
                    scenario.name(),
                    optimized.startupMedian(),
                    optimized.startupP95(),
                    optimized.couponSeedMedian(),
                    optimized.userSeedMedian(),
                    optimized.lookupMedian(),
                    optimized.insertMedian(),
                    optimized.lookupCallMedian()));
        }

        String markdown = """
                # Startup Benchmark (Docker)

                | scenario | mode | startup median (ms) | startup p95 (ms) | coupon seed median (ms) | user seed median (ms) | lookup median (ms) | insert median (ms) | lookup calls median |
                | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                %s
                """.formatted(String.join("\n", markdownRows));

        BenchmarkReportWriter.write("docker-startup-benchmark", payload, markdown);
    }

    private ModeStartupResult runModeForScenario(StartupScenario scenario, TestDataSeedMode mode) {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            runSingle(scenario, mode);
        }

        List<StartupSample> measured = new ArrayList<>();
        for (int i = 0; i < MEASURED_RUNS; i++) {
            measured.add(runSingle(scenario, mode));
        }

        return new ModeStartupResult(scenario, mode, measured);
    }

    private StartupSample runSingle(StartupScenario scenario, TestDataSeedMode mode) {
        long startedAt = System.nanoTime();
        ConfigurableApplicationContext context = new SpringApplicationBuilder(CouponSystemApplication.class)
                .properties(Map.ofEntries(
                        Map.entry("server.port", "0"),
                        Map.entry("spring.main.banner-mode", "off"),
                        Map.entry("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"),
                        Map.entry("spring.datasource.url", MYSQL.getJdbcUrl()),
                        Map.entry("spring.datasource.username", MYSQL.getUsername()),
                        Map.entry("spring.datasource.password", MYSQL.getPassword()),
                        Map.entry("spring.jpa.hibernate.ddl-auto", "create"),
                        Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "create"),
                        Map.entry("coupon.test-data.enabled", "true"),
                        Map.entry("coupon.test-data.seed-mode", mode.name().toLowerCase()),
                        Map.entry("coupon.test-data.coupon-count", String.valueOf(scenario.couponCount())),
                        Map.entry("coupon.test-data.user-count", String.valueOf(scenario.userCount())),
                        Map.entry("coupon.test-data.user-prefix", "bench-user-"),
                        Map.entry("coupon.test-data.user-password", "benchmark-pw")))
                .run();

        long startupMillis = (System.nanoTime() - startedAt) / 1_000_000;
        TestDataSeedingMetrics metrics = context.getBean(TestDataSeedingMetricsRecorder.class).latest();

        CouponRepository couponRepository = context.getBean(CouponRepository.class);
        UserRepository userRepository = context.getBean(UserRepository.class);
        long couponCount = couponRepository.count();
        long userCount = userRepository.count();

        context.close();
        return new StartupSample(startupMillis, metrics, couponCount, userCount);
    }

    private record StartupScenario(String name, int couponCount, int userCount) {
    }

    private record StartupSample(
            long startupMillis,
            TestDataSeedingMetrics metrics,
            long actualCouponCount,
            long actualUserCount) {
    }

    private record ModeStartupResult(StartupScenario scenario, TestDataSeedMode mode, List<StartupSample> samples) {
        long startupMedian() {
            return BenchmarkStats.median(samples.stream().map(StartupSample::startupMillis).toList());
        }

        long startupP95() {
            return BenchmarkStats.p95(samples.stream().map(StartupSample::startupMillis).toList());
        }

        long couponSeedMedian() {
            return BenchmarkStats.median(samples.stream().map(sample -> sample.metrics().couponSeedMillis()).toList());
        }

        long userSeedMedian() {
            return BenchmarkStats.median(samples.stream().map(sample -> sample.metrics().userSeedMillis()).toList());
        }

        long lookupMedian() {
            return BenchmarkStats.median(samples.stream().map(sample -> sample.metrics().userExistingLookupMillis()).toList());
        }

        long insertMedian() {
            return BenchmarkStats.median(samples.stream().map(sample -> sample.metrics().userInsertMillis()).toList());
        }

        long lookupCallMedian() {
            return BenchmarkStats.median(samples.stream().map(sample -> sample.metrics().userExistingLookupCallCount()).toList());
        }

        long insertedUsersMedian() {
            return BenchmarkStats.median(samples.stream().map(sample -> sample.metrics().insertedUserCount()).toList());
        }

        long assignedCouponSeedTarget() {
            return BenchmarkStats.median(samples.stream().map(sample -> Long.valueOf(sample.metrics().targetCouponCount())).toList());
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("scenario", scenario.name());
            map.put("mode", mode.name());
            map.put("startupMillisMedian", startupMedian());
            map.put("startupMillisP95", startupP95());
            map.put("couponSeedMillisMedian", couponSeedMedian());
            map.put("userSeedMillisMedian", userSeedMedian());
            map.put("lookupMillisMedian", lookupMedian());
            map.put("insertMillisMedian", insertMedian());
            map.put("lookupCallsMedian", lookupCallMedian());
            map.put("insertedUsersMedian", insertedUsersMedian());
            map.put("actualCouponCountMedian", BenchmarkStats.median(samples.stream().map(StartupSample::actualCouponCount).toList()));
            map.put("actualUserCountMedian", BenchmarkStats.median(samples.stream().map(StartupSample::actualUserCount).toList()));
            map.put("seedModeRecorded", samples.get(0).metrics().seedMode());
            return map;
        }
    }
}
