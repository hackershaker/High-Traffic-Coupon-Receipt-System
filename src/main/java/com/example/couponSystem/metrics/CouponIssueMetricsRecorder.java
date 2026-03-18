package com.example.couponSystem.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CouponIssueMetricsRecorder {

    // 모니터링 도구와 결합을 낮추는 MeterRegistry
    private final MeterRegistry meterRegistry;
    private Timer latencyTimer;

    public Timer.Sample startAttempt() {
        Counter.builder("coupon.issue.request.count")
                .description("Total coupon issuance attempts")
                .register(meterRegistry)
                .increment();
        return Timer.start(meterRegistry);
    }

    public void recordResult(String result) {
        Counter.builder("coupon.issue.result.count")
                .description("Coupon issuance result counts")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    public void recordException(Throwable throwable) {
        Counter.builder("coupon.issue.exception.count")
                .description("Coupon issuance exception counts")
                .tag("exception", throwable.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
    }

    public void stopAttempt(Timer.Sample sample) {
        sample.stop(latencyTimer());
    }

    private synchronized Timer latencyTimer() {
        if (latencyTimer == null) {
            latencyTimer = Timer.builder("coupon.issue.latency")
                    .description("Latency for coupon issuance attempts")
                    .publishPercentileHistogram()
                    .publishPercentiles(0.95, 0.99)
                    .register(meterRegistry);
        }
        return latencyTimer;
    }
}
