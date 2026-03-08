package com.example.couponSystem.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;

@Repository
public interface CouponRepository extends JpaRepository< Coupon, Long>{
    Optional<Coupon> findFirstByState(CouponState state);
}
