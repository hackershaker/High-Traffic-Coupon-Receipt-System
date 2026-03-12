package com.example.couponSystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.couponSystem.repository.CouponRepository;

@SpringBootTest
class CouponServiceTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @Transactional
    void createTestCoupons_createsExpectedAmount() {
        couponRepository.deleteAll();

        couponService.createTestCoupons(100);
        assertEquals(100, couponRepository.count());

        couponService.createTestCoupons(5);
        assertEquals(105, couponRepository.count());
    }

    @Test
    @Transactional
    void ensureTestCoupons_addsOnlyMissingAmount() {
        couponRepository.deleteAll();

        couponService.createTestCoupons(2);
        couponService.ensureTestCoupons(5);
        assertEquals(5, couponRepository.count());

        couponService.ensureTestCoupons(5);
        assertEquals(5, couponRepository.count());
    }
}
