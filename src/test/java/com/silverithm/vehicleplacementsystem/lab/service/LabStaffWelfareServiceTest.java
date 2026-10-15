package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.lab.dto.LabLedgerDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffWelfareDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBonusFundRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffWelfareRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabStaffWelfareServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 5);

    @Autowired private LabStaffWelfareService service;
    @Autowired private LabLedgerService ledgerService;
    @Autowired private LabStaffWelfareRepository welfare;
    @Autowired private LabBonusFundRepository funds;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member worker;
    private Member director;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        worker = LabTestFixtures.member(em, forest, "요양보호사", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        director = LabTestFixtures.member(em, forest, "원장", Member.Role.ADMIN, Member.MemberStatus.ACTIVE);
        access.companyId = forest.getId();
    }

    private LabStaffWelfareDto.WelfareInput welfare(Long memberId, Long ledgerId) {
        return new LabStaffWelfareDto.WelfareInput(DAY, memberId, "GIFT_CARD", "상품권 5만원", 50_000L, true, false,
                ledgerId, "회계 서류철 3권", null);
    }

    private LabStaffWelfareDto.BonusInput fund(String kind, Long amount, Long memberId, boolean ownerUse,
                                               Long ledgerId) {
        return new LabStaffWelfareDto.BonusInput(kind, DAY, amount, memberId, "직원 포상", ownerUse, ledgerId, null);
    }

    private Long ledgerOf(Company company) {
        Long previous = access.companyId;
        access.companyId = company.getId();
        Long id = ledgerService.create(new LabLedgerDto.Input(DAY, "EXPENSE", "311", "포상품 구입", 50_000L, "행복마트",
                "CARD", null, false)).id();
        access.companyId = previous;
        return id;
    }

    // 받은 직원은 같은 기관만 — 다른 기관·없는 직원은 404, 저장도 없다. 이름은 서버가 채운다
    @Test
    void memberMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member outsider = LabTestFixtures.member(em, other, "외부직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);
        assertRejected(() -> service.createWelfare(welfare(outsider.getId(), null)), HttpStatus.NOT_FOUND,
                "직원을 찾을 수 없습니다");
        assertRejected(() -> service.createWelfare(welfare(999_999L, null)), HttpStatus.NOT_FOUND,
                "직원을 찾을 수 없습니다");
        assertRejected(() -> service.createWelfare(welfare(null, null)), HttpStatus.BAD_REQUEST, "직원");
        assertRejected(() -> service.createFund(fund("SPENT", 10_000L, outsider.getId(), false, null)),
                HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertThat(welfare.count()).isZero();
        assertThat(funds.count()).isZero();

        LabStaffWelfareDto.WelfareView saved = service.createWelfare(welfare(worker.getId(), null));
        assertThat(saved.memberName()).isEqualTo("요양보호사");
        assertThat(saved.kind()).isEqualTo("GIFT_CARD");
        assertThat(service.listWelfare("2026")).extracting(LabStaffWelfareDto.WelfareView::memberName)
                .containsExactly("요양보호사");
        assertThat(service.listWelfare("2025")).isEmpty();

        // 다른 기관은 이 기록을 보지도 고치지도 지우지도 못한다
        access.companyId = other.getId();
        assertThat(service.listWelfare("2026")).isEmpty();
        assertRejected(() -> service.updateWelfare(saved.id(), welfare(outsider.getId(), null)), HttpStatus.NOT_FOUND,
                "찾을 수 없습니다");
        assertRejected(() -> service.deleteWelfare(saved.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        access.companyId = forest.getId();
        assertThat(welfare.count()).isEqualTo(1);

        LocalDate future = LabValidation.seoulToday().plusDays(1);
        assertRejected(() -> service.createWelfare(new LabStaffWelfareDto.WelfareInput(future, worker.getId(),
                "BONUS", "포상금", 1L, true, false, null, null, null)), HttpStatus.BAD_REQUEST, "제공일");
        assertRejected(() -> service.createWelfare(new LabStaffWelfareDto.WelfareInput(DAY, worker.getId(),
                "FREE_LUNCH", "포상금", 1L, true, false, null, null, null)), HttpStatus.BAD_REQUEST, "복지 종류");
    }

    // 증빙 장부 기록은 같은 기관 것만 — 다른 기관 것은 404. 복지·가산금 모두
    @Test
    void evidenceLedgerSameCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Long foreign = ledgerOf(other);
        Long mine = ledgerOf(forest);

        assertRejected(() -> service.createWelfare(welfare(worker.getId(), foreign)), HttpStatus.NOT_FOUND,
                "장부 기록을 찾을 수 없습니다");
        assertRejected(() -> service.createWelfare(welfare(worker.getId(), 999_999L)), HttpStatus.NOT_FOUND,
                "장부 기록을 찾을 수 없습니다");
        assertRejected(() -> service.createFund(fund("RECEIVED", 1_000_000L, null, false, foreign)),
                HttpStatus.NOT_FOUND, "장부 기록을 찾을 수 없습니다");
        assertThat(welfare.count()).isZero();
        assertThat(funds.count()).isZero();

        assertThat(service.createWelfare(welfare(worker.getId(), mine)).evidenceLedgerId()).isEqualTo(mine);
        assertThat(service.createFund(fund("SPENT", 50_000L, worker.getId(), false, mine)).evidenceLedgerId())
                .isEqualTo(mine);
    }

    // 대표자 겸 직원에게 쓴 금액(ownerUse)과 대표자 본인 수령은 그대로 저장되고, 받은 돈에는 직원·대표자 표시를 둘 수 없다
    @Test
    void bonusFundOwnerUseStored() {
        LabStaffWelfareDto.BonusView received = service.createFund(fund("RECEIVED", 3_000_000L, null, false, null));
        LabStaffWelfareDto.BonusView spent = service.createFund(fund("SPENT", 1_000_000L, worker.getId(), false,
                null));
        LabStaffWelfareDto.BonusView ownerSpent = service.createFund(fund("SPENT", 500_000L, director.getId(), true,
                null));
        LabStaffWelfareDto.BonusView team = service.createFund(fund("SPENT", 200_000L, null, false, null));

        assertThat(received.kind()).isEqualTo("RECEIVED");
        assertThat(spent.memberName()).isEqualTo("요양보호사");
        assertThat(ownerSpent.ownerUse()).isTrue();
        assertThat(ownerSpent.memberName()).isEqualTo("원장");
        assertThat(team.memberId()).isNull();
        assertThat(team.memberName()).isNull();
        assertThat(service.listFunds("2026")).hasSize(4);
        assertThat(service.listFunds("2026").stream().filter(LabStaffWelfareDto.BonusView::ownerUse)
                .mapToLong(LabStaffWelfareDto.BonusView::amount).sum()).isEqualTo(500_000L);

        LabStaffWelfareDto.BonusView fixed = service.updateFund(ownerSpent.id(),
                fund("SPENT", 400_000L, director.getId(), true, null));
        assertThat(fixed.amount()).isEqualTo(400_000L);

        assertRejected(() -> service.createFund(fund("RECEIVED", 1_000L, worker.getId(), false, null)),
                HttpStatus.BAD_REQUEST, "직원을 지정하지 않습니다");
        assertRejected(() -> service.createFund(fund("RECEIVED", 1_000L, null, true, null)), HttpStatus.BAD_REQUEST,
                "대표자 사용");
        assertRejected(() -> service.createFund(fund("SPENT", 0L, null, false, null)), HttpStatus.BAD_REQUEST, "금액");
        assertRejected(() -> service.createFund(fund("SPENT", 1_000_000_001L, null, false, null)),
                HttpStatus.BAD_REQUEST, "금액");

        // 복지 대장의 대표자 본인 수령도 그대로 저장된다
        LabStaffWelfareDto.WelfareView self = service.createWelfare(new LabStaffWelfareDto.WelfareInput(DAY,
                director.getId(), "BONUS", "포상금", 100_000L, true, true, null, null, null));
        assertThat(self.recipientIsOwner()).isTrue();
        assertThat(service.listFunds("2026")).extracting(LabStaffWelfareDto.BonusView::id)
                .containsAll(List.of(received.id(), spent.id()));
    }
}
