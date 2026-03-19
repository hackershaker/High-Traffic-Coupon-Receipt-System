package com.example.couponSystem.security;

/**
 * Captures phase-level timing and counts for the latest bootstrap seeding run.
 */
public record TestDataSeedingMetrics(
        String seedMode,
        int targetCouponCount,
        int targetUserCount,
        long totalMillis,
        long bootstrapUserMillis,
        long couponSeedMillis,
        long userSeedMillis,
        long userExistingLookupMillis,
        long userInsertMillis,
        long userExistingLookupCallCount,
        long existingUserCount,
        long insertedUserCount) {

    public static TestDataSeedingMetrics empty() {
        return new TestDataSeedingMetrics(
                "N/A",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
    }
}

