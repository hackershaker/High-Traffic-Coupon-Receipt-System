package com.example.couponSystem.security;

/**
 * Aggregates the measurable outcomes of one load-test user seeding pass.
 */
public record LoadTestUserSeedResult(
        long totalMillis,
        long existingLookupMillis,
        long insertMillis,
        long existingLookupCallCount,
        long existingUserCount,
        long insertedUserCount) {

    public static LoadTestUserSeedResult empty() {
        return new LoadTestUserSeedResult(0, 0, 0, 0, 0, 0);
    }
}

