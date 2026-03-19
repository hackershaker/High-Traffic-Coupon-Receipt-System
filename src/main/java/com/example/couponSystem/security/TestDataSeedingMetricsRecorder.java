package com.example.couponSystem.security;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Stores the most recent seeding metrics so benchmark tests can inspect them.
 */
@Component
public class TestDataSeedingMetricsRecorder {

    private final AtomicReference<TestDataSeedingMetrics> latestMetrics =
            new AtomicReference<>(TestDataSeedingMetrics.empty());

    public void record(TestDataSeedingMetrics metrics) {
        latestMetrics.set(metrics);
    }

    public TestDataSeedingMetrics latest() {
        return latestMetrics.get();
    }
}