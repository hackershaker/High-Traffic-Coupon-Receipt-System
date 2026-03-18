package com.example.couponSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.couponSystem.domain.Member;

@Repository
public interface UserRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("select m.username from Member m where m.username like concat(:prefix, '%')")
    List<String> findUsernamesByPrefix(@Param("prefix") String prefix);
}
