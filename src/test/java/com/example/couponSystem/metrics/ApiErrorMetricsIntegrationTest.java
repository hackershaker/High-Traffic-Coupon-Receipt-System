package com.example.couponSystem.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest
class ApiErrorMetricsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MeterRegistry meterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void incrementsErrorCounter_whenValidationFails() throws Exception {
        double before = taggedCounterValue(
                "coupon.api.error.count", "status", "400", "exception", "MethodArgumentNotValidException");

        mockMvc.perform(post("/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" \",\"password\":\"123\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        double after = taggedCounterValue(
                "coupon.api.error.count", "status", "400", "exception", "MethodArgumentNotValidException");
        assertThat(after).isEqualTo(before + 1.0);
    }

    private double taggedCounterValue(String name, String key1, String value1, String key2, String value2) {
        Counter counter = meterRegistry.find(name)
                .tag(key1, value1)
                .tag(key2, value2)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
