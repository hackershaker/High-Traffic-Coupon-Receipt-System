package com.example.couponSystem.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class CouponService {

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private UserRepository userRepository;

    @Transactional
    public String CouponIssuance(Long userId) {
        Member user = userRepository.getReferenceById(userId);

        List<Coupon> picked = couponRepository.findFirstNewForUpdate(PageRequest.of(0,1));
        if (picked.isEmpty()) {
            return "EMPTY";
        }

        Coupon coupon = picked.get(0);

        coupon.setState(CouponState.ASSIGNED);
        coupon.setMember(user);
        couponRepository.save(coupon);

        return "ISSUED";
    }

    @Transactional
    public String issueCouponForUsername(String username) {
        Member user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        return CouponIssuance(user.getId());
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
}
