package com.example.couponSystem.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;

@Tag("docker")
@Testcontainers
@SpringBootTest
class CouponControllerTest {

    @Container
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.0.45"))
            .withDatabaseName("coupon_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerMysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.test.database.replace", () -> "NONE");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "create");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        couponRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void issueForCurrentUser_returnsIssuedJsonAndAssignsCoupon() throws Exception {
        Member member = Member.builder()
                .username("coupon-user")
                .passwordHash("hash")
                .role("ROLE_USER")
                .build();
        Member savedMember = userRepository.save(member);

        Coupon coupon = new Coupon();
        coupon.setState(CouponState.NEW);
        couponRepository.save(coupon);

        mockMvc.perform(post("/coupon/me")
                        .with(user("coupon-user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ISSUED"));

        List<Coupon> assignedCoupons = couponRepository.findAll().stream()
                .filter(each -> each.getState() == CouponState.ASSIGNED)
                .toList();

        assertThat(assignedCoupons).hasSize(1);
        assertThat(assignedCoupons.get(0).getMember()).isNotNull();
        assertThat(assignedCoupons.get(0).getMember().getId()).isEqualTo(savedMember.getId());
    }

    @Test
    void issueForCurrentUser_returnsEmptyJson_whenNoCouponIsAvailable() throws Exception {
        Member member = Member.builder()
                .username("coupon-user")
                .passwordHash("hash")
                .role("ROLE_USER")
                .build();
        userRepository.save(member);

        mockMvc.perform(post("/coupon/me")
                        .with(user("coupon-user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("EMPTY"));
    }

    @Test
    void issueForCurrentUser_returnsStructuredNotFound_whenPrincipalUserIsMissing() throws Exception {
        mockMvc.perform(post("/coupon/me")
                        .with(user("unknown-user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found: unknown-user"))
                .andExpect(jsonPath("$.path").value("/coupon/me"));
    }

    @Test
    void issueForCurrentUser_redirectsToLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/coupon/me")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
    }

    @Test
    void legacyCouponEndpoint_isNotExposed() throws Exception {
        mockMvc.perform(post("/coupon")
                        .contentType("application/json")
                        .content("{\"userId\":1}")
                        .with(user("coupon-user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
