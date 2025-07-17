package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column
    private String phoneNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;
    
    @Column
    private String fcmToken;
    
    @Column
    private String department;
    
    @Column
    private String position;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private LocalDateTime lastLoginAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateRole(Role role) {
        this.role = role;
    }

    public enum Role {
        ADMIN,      // 관리자
        CAREGIVER,  // 요양보호사
        OFFICE,     // 사무직
        USER        // 일반 사용자
    }
    
    public enum MemberStatus {
        ACTIVE,     // 활성
        INACTIVE,   // 비활성
        SUSPENDED,  // 정지
        DELETED     // 탈퇴
    }
} 