package com.example.couponSystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.couponSystem.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

}
