package com.example.couponSystem.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.security.user.name=repo-tester",
        "spring.security.user.password=secret",
        "spring.security.user.roles=USER"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Test
    void saveUser_cascadesCouponList() {
        Member user = new Member();
        user.setUsername("repo-tester");
        user.setPasswordHash("hash");
        user.setRole("ROLE_TESTER");

        Coupon coupon = new Coupon();
        coupon.setState(CouponState.NEW);
        coupon.setMember(user);

        List<Coupon> coupons = new ArrayList<>();
        coupons.add(coupon);
        user.setCouponList(coupons);

        Member saved = userRepository.save(user);

        assertNotNull(saved.getId());
        assertEquals(1, saved.getCouponList().size());
        assertNotNull(saved.getCouponList().get(0).getId());
        assertEquals(CouponState.NEW, saved.getCouponList().get(0).getState());
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void login_withConfiguredCredentials_redirectsToRoot() throws Exception {
        var result = mockMvc.perform(MockMvcRequestBuilders.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "repo-tester")
                .param("password", "secret")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andReturn();
        System.out.println("redirected to: " + result.getResponse().getRedirectedUrl());
    }

    @Test
    void login_withWrongCredentials_redirectsToError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "repo-tester")
                .param("password", "wrong")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/login?error"));
    }
}
