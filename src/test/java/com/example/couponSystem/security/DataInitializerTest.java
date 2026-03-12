package com.example.couponSystem.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;

@SpringBootTest(properties = {
        "coupon.test-data.enabled=true",
        "coupon.test-data.coupon-count=7",
        "coupon.test-data.user-count=3",
        "coupon.test-data.user-prefix=seed-user-",
        "coupon.test-data.user-password=test123"
})
class DataInitializerTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    void seedsCouponsAndUsers_whenEnabled() {
        assertThat(couponRepository.count()).isEqualTo(7);
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(4); // default user + 3 load-test accounts
        assertThat(userRepository.findByUsername("seed-user-1")).isPresent();
        assertThat(userRepository.existsByUsername("user")).isTrue();

        dataInitializer.run();
        assertThat(couponRepository.count()).isEqualTo(7);
    }
}
