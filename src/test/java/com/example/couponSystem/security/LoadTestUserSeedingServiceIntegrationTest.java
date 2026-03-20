package com.example.couponSystem.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.couponSystem.config.TestDataProperties;
import com.example.couponSystem.config.TestDataSeedMode;
import com.example.couponSystem.repository.UserRepository;

@SpringBootTest
class LoadTestUserSeedingServiceIntegrationTest {

    @Autowired
    private LoadTestUserSeedingService seedingService;

    @Autowired
    private UserRepository userRepository;

    @ParameterizedTest
    @EnumSource(TestDataSeedMode.class)
    void seedMode_isIdempotent_whenRunTwice(TestDataSeedMode mode) {
        userRepository.deleteAll();

        TestDataProperties props = new TestDataProperties();
        props.setSeedMode(mode);
        props.setUserPrefix("seed-user-");
        props.setUserCount(120);
        props.setUserInsertBatchSize(25);

        LoadTestUserSeedResult first = seedingService.seedUsers(props, "hash");
        LoadTestUserSeedResult second = seedingService.seedUsers(props, "hash");

        assertThat(first.insertedUserCount()).isEqualTo(120);
        assertThat(second.insertedUserCount()).isEqualTo(0);
        assertThat(userRepository.count()).isEqualTo(120);
    }
}
