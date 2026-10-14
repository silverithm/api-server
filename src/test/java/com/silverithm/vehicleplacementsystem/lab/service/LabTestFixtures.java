package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Member;
import jakarta.persistence.EntityManager;

/** 테스트용 기관·어르신 만들기 */
final class LabTestFixtures {

    private LabTestFixtures() {
    }

    static Company company(EntityManager em, String name) {
        Company company = Company.of(name, "서울", null);
        em.persist(company);
        return company;
    }

    static Elderly elder(EntityManager em, Company company, String name) {
        Elderly elderly = new Elderly(name, false, company);
        em.persist(elderly);
        em.flush();
        return elderly;
    }

    /** 직원(Member) — 이름은 암호화 열이라 저장 후 읽을 때 평문으로 돌아온다 */
    static Member member(EntityManager em, Company company, String name, Member.Role role,
                         Member.MemberStatus status) {
        String key = "u" + System.nanoTime();
        Member member = Member.builder()
                .username(key)
                .password("pw")
                .name(name)
                .email(key + "@test.kr")
                .role(role)
                .status(status)
                .position("사회복지사")
                .company(company)
                .build();
        em.persist(member);
        em.flush();
        return member;
    }
}
