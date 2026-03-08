package com.example.couponSystem.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.User;
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
    public String CouponIssuance(Long userId){
        User user = userRepository.getReferenceById(userId);

        Optional<Coupon> coupon = couponRepository.findFirstByState(CouponState.NEW);
        if(coupon.isEmpty()){
            return "EMPTY";
        }

        Coupon registedCoupon = coupon.get();

        List<Coupon> couponList = user.getCouponList();
        registedCoupon.setState(CouponState.ASSIGNED);
        couponList.add(registedCoupon);

        user.setCouponList(couponList);
        
        couponRepository.save(registedCoupon);
        userRepository.save(user);

        return "ISSUED";

        
    }


}
