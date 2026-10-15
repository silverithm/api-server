package com.silverithm.vehicleplacementsystem.lab.service;

import org.springframework.stereotype.Component;

/**
 * 테스트용 접근 판정 — 항상 열려 있고, 요청자 기관을 테스트가 직접 정한다.
 *
 * <p>서비스마다 @MockBean을 쓰면 테스트 클래스마다 스프링 컨텍스트가 따로 만들어져 캐시가 불어나고
 * 전체 테스트가 힙 부족으로 죽는다. 이 가짜 빈을 모든 실험실 JPA 테스트가 공유해 컨텍스트를 하나로 둔다.
 */
@Component
public class LabTestAccess extends LabAccessService {

    public Long companyId = 1L;
    public String username = "admin@forest.kr";
    /** 작성자로 남는 사람 이름 — 로그인 식별자(이메일)가 아니어야 한다 */
    public String displayName = "숲속 관리자";

    public LabTestAccess() {
        super("4", null, null, null);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Caller requireCaller() {
        return new Caller(username, companyId);
    }

    @Override
    public String displayName(Caller caller) {
        return displayName;
    }

    @Override
    public Long requireCompanyId() {
        return companyId;
    }
}
