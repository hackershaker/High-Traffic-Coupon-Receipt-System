package com.example.couponSystem.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BenchmarkStats {

    private BenchmarkStats() {
    }

    static long median(List<Long> values) {
        return percentile(values, 50);
    }

    static long p95(List<Long> values) {
        return percentile(values, 95);
    }

    static long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0;
        }

        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        int normalized = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(normalized);
    }
}

