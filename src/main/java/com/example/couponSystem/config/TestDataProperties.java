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

    public String usernameForIndex(int index) {
        return userPrefix + index;
    }
}
