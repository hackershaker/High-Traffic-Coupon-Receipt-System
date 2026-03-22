package com.example.couponSystem.service;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.metrics.CouponIssueMetricsRecorder;
import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;

import io.micrometer.core.instrument.Timer;
import jakarta.transaction.Transactional;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final CouponIssueMetricsRecorder couponIssueMetricsRecorder;

    public CouponService(
            CouponRepository couponRepository,
            UserRepository userRepository,
            CouponIssueMetricsRecorder couponIssueMetricsRecorder) {
        this.couponRepository = couponRepository;
        this.userRepository = userRepository;
        this.couponIssueMetricsRecorder = couponIssueMetricsRecorder;
    }

    @Transactional
    public String issueCouponForUserId(Long userId) {
        return issueWithMetrics(() -> {
            Member user = userRepository.getReferenceById(userId);
            return issueCouponToMember(user);
        });
    }

    @Transactional
    public String issueCouponForUsername(String username) {
        return issueWithMetrics(() -> {
            Member user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
            return issueCouponToMember(user);
        });
    }

    @Transactional
    public void ensureTestCoupons(int targetCount) {
        long existing = couponRepository.count();
        if (existing >= targetCount) {
            return;
        }

        int delta = (int) (targetCount - existing);
        createTestCoupons(delta);
    }

    @Transactional
    public void createTestCoupons(int amount) {
        List<Coupon> coupons = IntStream.range(0, amount)
                .mapToObj(i -> {
                    Coupon coupon = new Coupon();
                    coupon.setState(CouponState.NEW);
                    return coupon;
                })
                .collect(Collectors.toList());

        couponRepository.saveAll(coupons);
    }

    private String issueCouponToMember(Member user) {
        List<Coupon> picked = couponRepository.findFirstNewForUpdate(PageRequest.of(0, 1));
        if (picked.isEmpty()) {
            return "EMPTY";
        }

        Coupon coupon = picked.get(0);
        coupon.setState(CouponState.ASSIGNED);
        coupon.setMember(user);
        couponRepository.save(coupon);
        return "ISSUED";
    }

    private String issueWithMetrics(Supplier<String> issueAction) {
        Timer.Sample sample = couponIssueMetricsRecorder.startAttempt();
        try {
            String result = issueAction.get();
            couponIssueMetricsRecorder.recordResult(result);
            return result;
        } catch (RuntimeException exception) {
            couponIssueMetricsRecorder.recordResult("ERROR");
            couponIssueMetricsRecorder.recordException(exception);
            throw exception;
        } finally {
            couponIssueMetricsRecorder.stopAttempt(sample);
        }
    }
}
