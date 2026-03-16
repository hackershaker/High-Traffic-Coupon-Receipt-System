package com.example.couponSystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class MemberRegisterRequest {
    @NotBlank(message = "username is required")
    @Size(min = 4, max = 50, message = "username must be between 4 and 50 characters")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
    private String password;
}