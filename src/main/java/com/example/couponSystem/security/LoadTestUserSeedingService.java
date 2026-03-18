package com.example.couponSystem.security;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.example.couponSystem.config.TestDataProperties;
import com.example.couponSystem.config.TestDataSeedMode;
import com.example.couponSystem.domain.Member;
import com.example.couponSystem.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoadTestUserSeedingService {

    private final UserRepository userRepository;

    public LoadTestUserSeedResult seedUsers(TestDataProperties props, String passwordHash) {
        if (props.getSeedMode() == TestDataSeedMode.BASELINE) {
            return seedUsersBaseline(props, passwordHash);
        }
        return seedUsersOptimized(props, passwordHash);
    }

    private LoadTestUserSeedResult seedUsersBaseline(TestDataProperties props, String passwordHash) {
        long startedAt = System.nanoTime();
        long lookupStartedAt = System.nanoTime();

        List<Member> usersToInsert = new ArrayList<>();
        int userCount = props.getUserCount();
        for (int i = 1; i <= userCount; i++) {
            String username = props.usernameForIndex(i);
            if (!userRepository.existsByUsername(username)) {
                usersToInsert.add(buildMember(username, passwordHash));
            }
        }

        long lookupMillis = elapsedMillis(lookupStartedAt);
        long insertStartedAt = System.nanoTime();
        usersToInsert.forEach(userRepository::save);
        long insertMillis = elapsedMillis(insertStartedAt);

        return new LoadTestUserSeedResult(
                elapsedMillis(startedAt),
                lookupMillis,
                insertMillis,
                userCount,
                userCount - usersToInsert.size(),
                usersToInsert.size());
    }

    private LoadTestUserSeedResult seedUsersOptimized(TestDataProperties props, String passwordHash) {
        long startedAt = System.nanoTime();
        long lookupStartedAt = System.nanoTime();

        Set<String> existingUsernames = new HashSet<>(userRepository.findUsernamesByPrefix(props.getUserPrefix()));
        long lookupMillis = elapsedMillis(lookupStartedAt);

        List<Member> usersToInsert = IntStream.rangeClosed(1, props.getUserCount())
                .mapToObj(props::usernameForIndex)
                .filter(username -> !existingUsernames.contains(username))
                .map(username -> buildMember(username, passwordHash))
                .collect(Collectors.toList());

        long insertStartedAt = System.nanoTime();
        saveInBatches(usersToInsert, props.getUserInsertBatchSize());
        long insertMillis = elapsedMillis(insertStartedAt);

        return new LoadTestUserSeedResult(
                elapsedMillis(startedAt),
                lookupMillis,
                insertMillis,
                1,
                existingUsernames.size(),
                usersToInsert.size());
    }

    private void saveInBatches(List<Member> users, int batchSize) {
        if (users.isEmpty()) {
            return;
        }

        int normalizedBatchSize = Math.max(1, batchSize);
        for (int from = 0; from < users.size(); from += normalizedBatchSize) {
            int to = Math.min(users.size(), from + normalizedBatchSize);
            userRepository.saveAll(users.subList(from, to));
        }
    }

    private Member buildMember(String username, String passwordHash) {
        return Member.builder()
                .username(username)
                .passwordHash(passwordHash)
                .role("ROLE_USER")
                .build();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

