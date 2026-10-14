package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffDto;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@LabJpaTest
class LabStaffServiceTest {

    @Autowired private LabStaffService service;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    // 같은 기관의 재직 직원만, 이름순으로, 네 칸(id·이름·역할·직책)만 돌려준다
    @Test
    void listsOnlyOwnCompanyStaff() {
        Company forest = LabTestFixtures.company(em, "숲속");
        Company other = LabTestFixtures.company(em, "다른기관");
        Member lee = LabTestFixtures.member(em, forest, "이요양", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        Member han = LabTestFixtures.member(em, forest, "한사무", Member.Role.OFFICE, Member.MemberStatus.ACTIVE);
        Member kang = LabTestFixtures.member(em, forest, "강관리", Member.Role.ADMIN, Member.MemberStatus.ACTIVE);
        LabTestFixtures.member(em, forest, "퇴사자", Member.Role.CAREGIVER, Member.MemberStatus.INACTIVE);
        LabTestFixtures.member(em, forest, "탈퇴자", Member.Role.CAREGIVER, Member.MemberStatus.DELETED);
        LabTestFixtures.member(em, other, "남직원", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        access.companyId = forest.getId();
        em.clear();

        List<LabStaffDto.View> staff = service.list();

        // 이름은 암호화 열인데도 평문으로 읽혀 가나다순으로 정렬된다
        assertThat(staff).extracting(LabStaffDto.View::name).containsExactly("강관리", "이요양", "한사무");
        assertThat(staff).extracting(LabStaffDto.View::id).containsExactly(kang.getId(), lee.getId(), han.getId());
        assertThat(staff).extracting(LabStaffDto.View::role).containsExactly("ADMIN", "CAREGIVER", "OFFICE");
        assertThat(staff).extracting(LabStaffDto.View::position).containsOnly("사회복지사");

        // 다른 기관 요청자에게는 그 기관 직원만 보인다
        access.companyId = other.getId();
        assertThat(service.list()).extracting(LabStaffDto.View::name).containsExactly("남직원");
        access.companyId = forest.getId() + 1000;
        assertThat(service.list()).isEmpty();
    }
}
