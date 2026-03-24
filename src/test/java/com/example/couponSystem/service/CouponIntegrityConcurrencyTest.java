package com.example.couponSystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.couponSystem.domain.Coupon;
import com.example.couponSystem.domain.CouponState;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.CouponRepository;
import com.example.couponSystem.repository.UserRepository;

@SpringBootTest
class CouponIntegrityConcurrencyTest {

    private static final int COUPON_COUNT = 100;
    private static final int TOTAL_REQUEST_COUNT = 5000;
    private static final int WORKER_THREAD_COUNT = 200;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void extremeConcurrency_preservesCouponDataIntegrity() throws InterruptedException {
        couponRepository.deleteAll();
        userRepository.deleteAll();

        couponService.createTestCoupons(COUPON_COUNT);

        List<Member> users = IntStream.rangeClosed(1, TOTAL_REQUEST_COUNT)
                .mapToObj(i -> Member.builder()
                        .username("concurrency-user-" + i)
                        .passwordHash("hash")
                        .role("ROLE_USER")
                        .couponList(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());
        List<Member> savedUsers = userRepository.saveAll(users);

        AtomicInteger issuedCount = new AtomicInteger();
        AtomicInteger emptyCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(savedUsers.size());
        // 워커 WORKER_THREAD_COUNT개 + 작업 대기 큐를 만듦.
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_THREAD_COUNT); 

        for (Member member : savedUsers) {
            executor.submit(() -> {
                try {
                    start.await();
                    String result = couponService.issueCouponForUserId(member.getId());
                    if ("ISSUED".equals(result)) {
                        issuedCount.incrementAndGet();
                    } else if ("EMPTY".equals(result)) {
                        emptyCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failedCount.incrementAndGet();
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        // Simulate burst arrival: all requests are queued before workers start processing.
        start.countDown();
        assertThat(done.await(180, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        List<Coupon> coupons = couponRepository.findAll();
        long assignedCoupons = coupons.stream()
                .filter(coupon -> coupon.getState() == CouponState.ASSIGNED)
                .count();

        Long assignedWithMember = jdbcTemplate.queryForObject(
                """
                select count(*)
                from coupon c
                where c.state = 'ASSIGNED' and c.member_id is not null
                """,
                Long.class);

        Long membersHoldingMultipleAssignedCoupons = jdbcTemplate.queryForObject(
                """
                select count(*)
                from (
                    select c.member_id
                    from coupon c
                    where c.state = 'ASSIGNED' and c.member_id is not null
                    group by c.member_id
                    having count(*) > 1
                ) duplicated_member
                """,
                Long.class);

        assertThat(issuedCount.get() + emptyCount.get() + failedCount.get()).isEqualTo(TOTAL_REQUEST_COUNT);
        assertThat(failedCount.get()).isZero();
        assertThat(issuedCount.get()).isLessThanOrEqualTo(COUPON_COUNT);
        assertThat(assignedCoupons).isEqualTo(COUPON_COUNT);
        assertThat(issuedCount.get()).isEqualTo((int) assignedCoupons);

        // Every assigned coupon should map to exactly one member and no member gets more than one assigned coupon.
        assertThat(assignedWithMember).isEqualTo(assignedCoupons);
        assertThat(membersHoldingMultipleAssignedCoupons).isZero();
    }
}

