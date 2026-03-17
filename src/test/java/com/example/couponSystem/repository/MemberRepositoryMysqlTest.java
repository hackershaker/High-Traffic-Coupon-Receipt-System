package com.example.couponSystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;

@Tag("docker")
@Testcontainers
@SpringBootTest
@Transactional
class MemberRepositoryMysqlTest {

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
        registry.add("spring.datasource.hikari.connection-timeout", () -> "300000");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "600000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "300000");
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "create");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void memberIsPersistedInMysqlContainer() {
        Member member = Member.builder()
                .username("tc-user")
                .passwordHash("hash")
                .role("ROLE_TEST")
                .build();

        Member saved = userRepository.save(member);

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findByUsername("tc-user")).isPresent();
    }

    @Test
    void skipLockedQueryReturnsANewCoupon() {
        Coupon coupon = new Coupon();
        coupon.setState(CouponState.NEW);
        couponRepository.save(coupon);

        var found = couponRepository.findFirstNewForUpdateSkipLocked();

        assertThat(found).isPresent();
        assertThat(found.get().getState()).isEqualTo(CouponState.NEW);
    }
}
