package com.example.couponSystem.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.couponSystem.config.TestDataProperties;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.UserRepository;
import com.example.couponSystem.service.CouponService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String BOOTSTRAP_USERNAME = "user";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CouponService couponService;
    private final TestDataProperties testDataProperties;
    private final LoadTestUserSeedingService loadTestUserSeedingService;
    private final TestDataSeedingMetricsRecorder metricsRecorder;

    @Override
    public void run(String... args) {
        long totalStartedAt = System.nanoTime();
        long bootstrapStartedAt = System.nanoTime();
        ensureBootstrapUser();
        long bootstrapMillis = elapsedMillis(bootstrapStartedAt);

        long couponSeedMillis = 0;
        LoadTestUserSeedResult userSeedResult = LoadTestUserSeedResult.empty();

        if (testDataProperties.isEnabled()) {
            long couponSeedStartedAt = System.nanoTime();
            couponService.ensureTestCoupons(testDataProperties.getCouponCount());
            couponSeedMillis = elapsedMillis(couponSeedStartedAt);

            String passwordHash = passwordEncoder.encode(testDataProperties.getUserPassword());
            userSeedResult = loadTestUserSeedingService.seedUsers(testDataProperties, passwordHash);
        }

        metricsRecorder.record(new TestDataSeedingMetrics(
                testDataProperties.getSeedMode().name(),
                testDataProperties.getCouponCount(),
                testDataProperties.getUserCount(),
                elapsedMillis(totalStartedAt),
                bootstrapMillis,
                couponSeedMillis,
                userSeedResult.totalMillis(),
                userSeedResult.existingLookupMillis(),
                userSeedResult.insertMillis(),
                userSeedResult.existingLookupCallCount(),
                userSeedResult.existingUserCount(),
                userSeedResult.insertedUserCount()));
    }

    private void ensureBootstrapUser() {
        if (userRepository.existsByUsername(BOOTSTRAP_USERNAME)) {
            return;
        }

        Member user = Member.builder()
                .username(BOOTSTRAP_USERNAME)
                .passwordHash(passwordEncoder.encode("change-me"))
                .role("ROLE_USER")
                .build();

        userRepository.save(user);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
