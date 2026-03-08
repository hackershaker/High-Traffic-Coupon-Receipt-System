package com.example.couponSystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.couponSystem.dto.CouponRequest;
import com.example.couponSystem.dto.CouponResponse;
import com.example.couponSystem.service.CouponService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
public class CouponController {
    
    @Autowired
    private CouponService couponService;

    @PostMapping("/coupon")
    public ResponseEntity<CouponResponse> RegisterCoupon(@RequestBody CouponRequest request) {

        String result = couponService.CouponIssuance(request.getUserId());
        
        CouponResponse response = new CouponResponse();
        response.setResult(result);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
}
