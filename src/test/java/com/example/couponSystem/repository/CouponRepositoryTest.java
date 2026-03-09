package com.example.couponSystem.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;

@SpringBootTest
@Transactional
class CouponRepositoryTest {

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void findFirstByState_shouldReturnFirstMatchingCoupon() {
        couponRepository.deleteAll();

        Coupon assigned = new Coupon();
        assigned.setState(CouponState.ASSIGNED);
        Coupon savedAssigned = couponRepository.save(assigned);

        Coupon fresh = new Coupon();
        fresh.setState(CouponState.NEW);        
        Coupon savedFresh = couponRepository.save(fresh);

        Coupon anotherFresh = new Coupon();
        anotherFresh.setState(CouponState.NEW);
        Coupon savedAnotherFresh = couponRepository.save(anotherFresh);

        var found = couponRepository.findFirstByState(CouponState.NEW);

        assertTrue(found.isPresent());
        assertEquals(savedFresh.getId(), found.get().getId());
    }
}
