package com.example.couponSystem.metrics;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ApiErrorMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public void record(HttpStatus status, Exception exception) {
        Counter.builder("coupon.api.error.count")
                .description("HTTP error counts handled by global exception advice")
                .tag("status", String.valueOf(status.value()))
                .tag("exception", exception.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
    }
}
