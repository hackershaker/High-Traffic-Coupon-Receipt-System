package com.example.couponSystem.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;
import com.example.couponSystem.service.CouponService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@SpringBootTest
class CouponIssueMetricsIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void recordsIssuedAndEmptyResults_withLatencyAndRequestCount() {
        Member member = userRepository.save(Member.builder()
                .username("metric-user")
                .passwordHash("hash")
                .role("ROLE_USER")
                .build());

        Coupon coupon = new Coupon();
        coupon.setState(CouponState.NEW);
        couponRepository.save(coupon);

        double requestBefore = counterValue("coupon.issue.request.count");
        double issuedBefore = taggedCounterValue("coupon.issue.result.count", "result", "ISSUED");
        double emptyBefore = taggedCounterValue("coupon.issue.result.count", "result", "EMPTY");
        long latencyCountBefore = timerCount("coupon.issue.latency");

        assertThat(couponService.issueCouponForUserId(member.getId())).isEqualTo("ISSUED");
        assertThat(couponService.issueCouponForUserId(member.getId())).isEqualTo("EMPTY");

        assertThat(counterValue("coupon.issue.request.count")).isEqualTo(requestBefore + 2.0);
        assertThat(taggedCounterValue("coupon.issue.result.count", "result", "ISSUED"))
                .isEqualTo(issuedBefore + 1.0);
        assertThat(taggedCounterValue("coupon.issue.result.count", "result", "EMPTY"))
                .isEqualTo(emptyBefore + 1.0);
        assertThat(timerCount("coupon.issue.latency")).isEqualTo(latencyCountBefore + 2L);
    }

    @Test
    void recordsErrorResultAndException_whenIssuanceFails() {
        double errorBefore = taggedCounterValue("coupon.issue.result.count", "result", "ERROR");
        double exceptionBefore = taggedCounterValue(
                "coupon.issue.exception.count", "exception", "ResponseStatusException");

        assertThatThrownBy(() -> couponService.issueCouponForUsername("unknown-user"))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(taggedCounterValue("coupon.issue.result.count", "result", "ERROR"))
                .isEqualTo(errorBefore + 1.0);
        assertThat(taggedCounterValue("coupon.issue.exception.count", "exception", "ResponseStatusException"))
                .isEqualTo(exceptionBefore + 1.0);
    }

    private double counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double taggedCounterValue(String name, String key, String value) {
        Counter counter = meterRegistry.find(name).tag(key, value).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private long timerCount(String name) {
        Timer timer = meterRegistry.find(name).timer();
        return timer == null ? 0L : timer.count();
    }
}

