package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareDeliveryDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareDeliveryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@LabJpaTest
class LabCareDeliveryServiceTest {

    @Autowired private LabCareDeliveryService service;
    @Autowired private LabCareDeliveryRepository deliveries;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Elderly kim;

    @BeforeEach
    void setUp() {
        Company forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    // 어르신·월당 한 건만 남고, 다시 저장하면 전달 내역이 바뀐다
    @Test
    void upsertByElderAndMonth() {
        service.upsert(new LabCareDeliveryDto.Input(kim.getId(), "2026-09", LocalDate.of(2026, 10, 2), "MAIL",
                "김철수", "등기 발송"));
        LabCareDeliveryDto.View second = service.upsert(new LabCareDeliveryDto.Input(kim.getId(), "2026-09",
                LocalDate.of(2026, 10, 3), "VISIT", "김철수", null));

        assertThat(deliveries.count()).isEqualTo(1);
        assertThat(second.method()).isEqualTo("VISIT");
        assertThat(second.elderlyName()).isEqualTo("김순자");
        assertThat(service.listByMonth("2026-09")).hasSize(1);
        assertThat(service.listByMonth("2026-08")).isEmpty();
    }

    // 전달 방법·월 형식이 틀리면 400
    @Test
    void validates() {
        assertThatThrownBy(() -> service.upsert(new LabCareDeliveryDto.Input(kim.getId(), "2026-09",
                LocalDate.of(2026, 10, 2), "PIGEON", null, null))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.upsert(new LabCareDeliveryDto.Input(kim.getId(), "2026-9",
                LocalDate.of(2026, 10, 2), "MAIL", null, null))).isInstanceOf(CustomException.class);
        assertThat(deliveries.count()).isZero();
    }
}
