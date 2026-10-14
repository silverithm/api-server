package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
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
}
