package com.example.couponSystem.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.couponSystem.dto.CouponResponse;
import com.example.couponSystem.service.CouponService;

@RestController
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/coupon/me")
    public ResponseEntity<CouponResponse> registerForCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        String result = couponService.issueCouponForUsername(userDetails.getUsername());

        CouponResponse response = new CouponResponse();
        response.setResult(result);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
 
