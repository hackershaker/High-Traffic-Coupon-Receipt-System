package com.example.couponSystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.couponSystem.dto.MemberRegisterRequest;
import com.example.couponSystem.service.MemberService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/signup")
        public ResponseEntity<Object> signup(@RequestBody MemberRegisterRequest request) {
            memberService.registerMember(request);
            return ResponseEntity.created(null).build();
        }
}
