package com.example.couponSystem.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.User;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveUser_cascadesCouponList() {
        User user = new User();
        user.setUserName("repo-tester");

        Coupon coupon = new Coupon();
        coupon.setState(CouponState.NEW);
        coupon.setUser(user);

        List<Coupon> coupons = new ArrayList<>();
        coupons.add(coupon);
        user.setCouponList(coupons);

        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
        assertEquals(1, saved.getCouponList().size());
        assertNotNull(saved.getCouponList().get(0).getId());
        assertEquals(CouponState.NEW, saved.getCouponList().get(0).getState());
    }
}
