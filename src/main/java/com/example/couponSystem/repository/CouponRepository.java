package com.example.couponSystem.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;

import jakarta.persistence.LockModeType;

@Repository
public interface CouponRepository extends JpaRepository< Coupon, Long>{
    Optional<Coupon> findFirstByState(CouponState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.state = com.example.couponSystem.domain.CouponState.NEW order by c.id asc")
    List<Coupon> findFirstNewForUpdate(Pageable pageable);

    // Alternative path for high load: skip already-locked rows instead of waiting.
    @Query(value = """
            select c.*
            from coupon c
            where c.state = 'NEW'
            order by c.id asc
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<Coupon> findFirstNewForUpdateSkipLocked();
}
