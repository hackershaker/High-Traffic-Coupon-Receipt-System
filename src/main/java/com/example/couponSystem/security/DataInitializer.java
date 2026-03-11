package com.example.couponSystem.security;

import java.util.stream.IntStream;

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

    @Override
    public void run(String... args) {
        ensureBootstrapUser();

        if (testDataProperties.isEnabled()) {
            couponService.ensureTestCoupons(testDataProperties.getCouponCount());
            createLoadTestUsers();
        }
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

    private void createLoadTestUsers() {
        String passwordHash = passwordEncoder.encode(testDataProperties.getUserPassword());
        IntStream.rangeClosed(1, testDataProperties.getUserCount())
                .mapToObj(testDataProperties::usernameForIndex)
                .filter(username -> !userRepository.existsByUsername(username))
                .map(username -> Member.builder()
                        .username(username)
                        .passwordHash(passwordHash)
                        .role("ROLE_USER")
                        .build())
                .forEach(userRepository::save);
    }
}
