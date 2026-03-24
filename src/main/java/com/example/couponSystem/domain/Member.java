package com.example.couponSystem.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table( uniqueConstraints = { @UniqueConstraint(name = "uk_user_username", columnNames = "username") })
@Builder
// Hibernate/JPA instantiate entities via reflection,
// so they require a default (no-arguments) constructor with at least package
// visibility.
@NoArgsConstructor
// Lombok’s @Builder (and any place where you want to create a User in one call)
// relies on a constructor that takes every field.
// @AllArgsConstructor supplies that constructor,
// which the generated builder uses behind the scenes and
// which makes it easy to instantiate User with all required properties in tests
// or services.
@AllArgsConstructor
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String role;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Default
    @OneToMany(mappedBy = "member", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<Coupon> couponList = new ArrayList<>();

    @PrePersist
    void applyDefaultStatus() {
        if (status == null) {
            status = MemberStatus.ACTIVE;
        }
    }

    public MemberStatus effectiveStatus() {
        return status == null ? MemberStatus.ACTIVE : status;
    }
}
