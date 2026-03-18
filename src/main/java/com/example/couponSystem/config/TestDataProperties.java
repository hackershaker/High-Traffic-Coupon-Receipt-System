package com.example.couponSystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Externalizes the knobs that control whether the application should bootstrap
 * a batch of coupons and users for load/concurrency tests.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "coupon.test-data")
public class TestDataProperties {
    /** Activation flag; defaults to {@code false} so production builds stay quiet. */
    private boolean enabled = false;
    /** Number of mock users to create when seeding. */
    private int userCount = 50;
    /** Number of coupons to pre-create for issuance tests. */
    private int couponCount = 100;
    /** Password that is shared by all generated load-test users. */
    private String userPassword = "loadtest";
    /** Prefix used to generate the username for each generated account. */
    private String userPrefix = "load-test-user-";
    /** Selects the test user seeding strategy (baseline or optimized). */
    private TestDataSeedMode seedMode = TestDataSeedMode.OPTIMIZED;
    /** Batch size used by the optimized user seeding path. */
    private int userInsertBatchSize = 500;
    /** Default benchmark scenario (small) user count. */
    private int benchmarkSmallUserCount = 1000;
    /** Default benchmark scenario (small) coupon count. */
    private int benchmarkSmallCouponCount = 1000;
    /** Default benchmark scenario (large) user count. */
    private int benchmarkLargeUserCount = 10000;
    /** Default benchmark scenario (large) coupon count. */
    private int benchmarkLargeCouponCount = 10000;

    public String usernameForIndex(int index) {
        return userPrefix + index;
    }
}
