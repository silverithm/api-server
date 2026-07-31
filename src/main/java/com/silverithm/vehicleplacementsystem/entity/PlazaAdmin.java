package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 케어브이 광장 운영자.
 * 광장은 전 기관 공유 리소스라 기관 관리자(company admin)와는 별개의 권한이다.
 * 여기 등록된 계정만 [운영] 공지 작성과 타인 글 삭제를 할 수 있다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "plaza_admins")
public class PlazaAdmin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 이메일 — JWT subject와 같은 값 */
    @Column(nullable = false, unique = true)
    private String email;

    /** 운영 화면에서 누구인지 알아보기 위한 메모용 이름 */
    @Column(length = 100)
    private String displayName;

    @Column(length = 255)
    private String memo;
}
