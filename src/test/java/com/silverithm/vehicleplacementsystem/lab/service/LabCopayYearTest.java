package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabCopayYearTest {

    @Autowired private LabCopayBillService service;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
        access.companyId = forest.getId();
    }

    private void bill(Elderly elder, String month) {
        service.upsert(new LabCopayBillDto.Input(elder.getId(), month, "3", "standard", "general", "h8to10", 20,
                60000, 1200000, 100000, 0, null, 0));
    }

    // 그 해 1~12월 청구서를 모두 — 전년 12월·다음 해 1월과 다른 기관 것은 섞이지 않는다
    @Test
    void yearFilterReturnsAllMonthsOfThatYear() {
        bill(kim, "2025-12");
        bill(kim, "2026-01");
        bill(park, "2026-01");
        bill(kim, "2026-06");
        bill(kim, "2026-12");
        bill(kim, "2027-01");

        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        access.companyId = other.getId();
        bill(stranger, "2026-03");
        access.companyId = forest.getId();

        var year = service.listByYear("2026");
        assertThat(year).extracting(LabCopayBillDto.View::billMonth)
                .containsExactly("2026-01", "2026-01", "2026-06", "2026-12");
        // 같은 달 안에서는 이름순
        assertThat(year).extracting(LabCopayBillDto.View::elderlyName).containsExactly("김순자", "박영수", "김순자", "김순자");
        assertThat(service.listByYear("2027")).hasSize(1);
        assertThat(service.listByYear("2024")).isEmpty();
        // 월 조회는 그대로 동작한다
        assertThat(service.listByMonth("2026-01")).hasSize(2);

        assertThatThrownBy(() -> service.listByYear("26")).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.listByYear("2026-01")).isInstanceOf(CustomException.class);
    }
}
