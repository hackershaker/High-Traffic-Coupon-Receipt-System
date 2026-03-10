package com.example.couponSystem.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.couponSystem.domain.Member;
import com.example.couponSystem.dto.MemberRegisterRequest;
import com.example.couponSystem.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void registerMember(MemberRegisterRequest request){
        if(userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 아이디입니다.");
        }

        Member member = Member.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .build();

        userRepository.save(member);
    }
}
