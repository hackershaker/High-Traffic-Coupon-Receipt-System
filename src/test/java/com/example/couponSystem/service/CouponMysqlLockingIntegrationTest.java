package com.example.couponSystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class CouponMysqlLockingIntegrationTest {

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
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void lockWait_blocksUntilPriorLockIsReleased() throws Exception {
        String lockName = "coupon-lock-wait-test";

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection locker = dataSource.getConnection()) {
            assertThat(acquireNamedLock(locker, lockName, 5)).isEqualTo(1L);

            Future<Long> waitingFuture = executor.submit(() -> {
                try (Connection waitingConnection = dataSource.getConnection()) {
                    long startedAt = System.nanoTime();
                    long lockResult = acquireNamedLock(waitingConnection, lockName, 5);
                    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

                    assertThat(lockResult).isEqualTo(1L);
                    releaseNamedLock(waitingConnection, lockName);
                    return elapsedMillis;
                }
            });

            Thread.sleep(1200);
            assertThat(waitingFuture.isDone()).isFalse();

            releaseNamedLock(locker, lockName);
            Long elapsedMillis = waitingFuture.get(10, TimeUnit.SECONDS);
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(900);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lockTimeout_raisesMySqlTimeoutError_whenWaitExceedsSessionLimit() throws Exception {
        Coupon coupon = createNewCoupon();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection locker = openTransactionConnection()) {
            touchCouponRow(locker, coupon.getId());

            Future<Throwable> timeoutFuture = executor.submit(() -> {
                try (Connection waiter = openTransactionConnection()) {
                    try (Statement statement = waiter.createStatement()) {
                        statement.execute("set session innodb_lock_wait_timeout = 1");
                    }
                    touchCouponRow(waiter, coupon.getId());
                    waiter.commit();
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });

            Throwable thrown = timeoutFuture.get(10, TimeUnit.SECONDS);
            assertThat(thrown).isNotNull();
            assertThat(containsMessage(thrown, "Lock wait timeout exceeded")).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deadlockScenario_releasesVictimTransaction_andSystemCanContinueIssuance() throws Exception {
        Member member = createUser("deadlock-user");
        Coupon first = createNewCoupon();
        Coupon second = createNewCoupon();

        CountDownLatch bothFirstLocksAcquired = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> txA = executor.submit(() -> runDeadlockWorker(first.getId(), second.getId(),
                    bothFirstLocksAcquired));
            Future<Throwable> txB = executor.submit(() -> runDeadlockWorker(second.getId(), first.getId(),
                    bothFirstLocksAcquired));

            Throwable a = txA.get(10, TimeUnit.SECONDS);
            Throwable b = txB.get(10, TimeUnit.SECONDS);

            int deadlockCount = 0;
            if (a != null && containsMessage(a, "Deadlock found")) {
                deadlockCount++;
            }
            if (b != null && containsMessage(b, "Deadlock found")) {
                deadlockCount++;
            }
            assertThat(deadlockCount).isGreaterThanOrEqualTo(1);

            String issuanceResult = couponService.issueCouponForUserId(member.getId());
            assertThat(issuanceResult).isEqualTo("ISSUED");

            long assignedCount = couponRepository.findAll().stream()
                    .filter(coupon -> coupon.getState() == CouponState.ASSIGNED)
                    .count();
            assertThat(assignedCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable runDeadlockWorker(Long firstCouponId, Long secondCouponId, CountDownLatch latch) {
        try (Connection connection = openTransactionConnection()) {
            touchCouponRow(connection, firstCouponId);
            latch.countDown();

            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("Failed to prepare deadlock scenario within timeout.");
            }

            touchCouponRow(connection, secondCouponId);
            connection.commit();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private Connection openTransactionConnection() throws Exception {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private void touchCouponRow(Connection connection, Long couponId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "update coupon set state = state where id = ?")) {
            statement.setLong(1, couponId);
            statement.executeUpdate();
        }
    }

    private long acquireNamedLock(Connection connection, String lockName, int timeoutSeconds) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select get_lock(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, timeoutSeconds);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long releaseNamedLock(Connection connection, String lockName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select release_lock(?)")) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private boolean containsMessage(Throwable throwable, String token) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(token)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Member createUser(String username) {
        Member member = Member.builder()
                .username(username)
                .passwordHash("hash")
                .role("ROLE_USER")
                .build();
        return userRepository.save(member);
    }

    private Coupon createNewCoupon() {
        Coupon coupon = new Coupon();
        coupon.setState(CouponState.NEW);
        return couponRepository.save(coupon);
    }
}

