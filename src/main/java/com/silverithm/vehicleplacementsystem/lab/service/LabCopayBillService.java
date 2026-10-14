package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCopayBill;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCopayBillRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 본인부담금 청구·수납.
 *
 * <p>화면은 수가 계산기로 급여비용·본인부담금(한도 안/초과)을 구해 보내고, 서버는 그 부품만 믿는다.
 * 비급여 합계·청구액·상태는 클라이언트 값을 받지 않고 여기서 다시 계산한다 — 합계가 어긋난 청구서가
 * 저장돼 수납 상태가 잘못 표시되는 일을 막으려는 것이다.
 */
@Service
public class LabCopayBillService {

    private static final Set<String> GRADES = Set.of("1", "2", "3", "4", "5", "cognitive");
    private static final Set<String> CLASSES = Set.of("standard", "reduced40", "reduced60", "medicalAid");
    private static final Set<String> UNITS = Set.of("general", "dementia");
    private static final Set<String> BANDS = Set.of("h3to6", "h6to8", "h8to10", "h10to13", "over13");

    private static final int MAX_ITEMS = 20;
    private static final int MAX_LABEL = 40;
    /** 금액 한 칸의 상한(1억 원) — 오타로 자리를 더 붙인 값과 int 넘침을 함께 막는다 */
    private static final int MAX_AMOUNT = 100_000_000;
    /** 청구액 합계의 상한. 부품 4종 + 비급여 20개가 모두 상한이어도 int 안에 들어오도록 따로 잡는다 */
    private static final long MAX_TOTAL = 1_000_000_000L;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabCopayBillRepository bills;
    private final TransactionTemplate tx;

    public LabCopayBillService(LabAccessService access, LabElderDirectory elders, LabCopayBillRepository bills,
                               PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.bills = bills;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public List<LabCopayBillDto.View> listByMonth(String month) {
        Long companyId = access.requireCompanyId();
        String ym = LabValidation.month(month, "월");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return bills.findByCompanyIdAndBillMonth(companyId, ym).stream()
                .map(b -> toView(b, names.getOrDefault(b.getElderlyId(), "")))
                .sorted(Comparator.comparing(LabCopayBillDto.View::elderlyName))
                .toList();
    }

    /** 청구서 저장. 같은 어르신·월이면 내용만 고치고 이미 받은 금액(paidAmount)은 그대로 둔다. */
    public LabCopayBillDto.View upsert(LabCopayBillDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("청구 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);

        String month = LabValidation.month(input.billMonth(), "청구 월");
        String grade = LabValidation.oneOf(input.careGrade(), GRADES, "장기요양등급");
        String copayClass = LabValidation.oneOf(input.copayClass(), CLASSES, "본인부담 구분");
        String unit = LabValidation.oneOf(input.daycareUnit(), UNITS, "주야간보호 유형");
        String band = LabValidation.oneOf(input.hourBand(), BANDS, "이용 시간대");
        int days = LabValidation.required(input.days(), "이용 일수");
        if (days < 0 || days > 31) {
            throw LabValidation.bad("이용 일수는 0~31일이어야 합니다");
        }
        int dailyFee = amount(LabValidation.required(input.dailyFee(), "1일 급여비용"), "1일 급여비용");
        int coveredTotal = amount(LabValidation.required(input.coveredTotal(), "급여비용 총액"), "급여비용 총액");
        int copay = amount(LabValidation.required(input.copayAmount(), "본인부담금"), "본인부담금");
        int overLimit = amount(input.overLimitAmount() == null ? 0 : input.overLimitAmount(), "한도 초과 금액");
        int previousUnpaid = amount(input.previousUnpaid() == null ? 0 : input.previousUnpaid(), "전월 미수금");

        List<LabCopayBillDto.NonCoveredItem> items = cleanItems(input.nonCoveredItems());
        long nonCovered = items.stream().mapToLong(LabCopayBillDto.NonCoveredItem::amount).sum();
        long totalDue = (long) copay + overLimit + nonCovered + previousUnpaid;
        if (totalDue > MAX_TOTAL) {
            throw LabValidation.bad("청구 합계가 너무 큽니다. 금액을 확인해 주세요");
        }
        String itemsJson = LabJson.write(items);

        LabCopayBill saved = LabValidation.upsertWithRetry(tx, () -> {
            LabCopayBill bill = bills.findByElderlyIdAndBillMonth(input.elderlyId(), month)
                    // 수납과 겹쳐도 한쪽 갱신이 사라지지 않게 기존 행은 잠그고 다시 읽는다
                    .flatMap(existing -> bills.findLockedByIdAndCompanyId(existing.getId(), companyId))
                    .orElseGet(() -> new LabCopayBill(companyId, input.elderlyId(), month));
            bill.fillBill(grade, copayClass, unit, band, days, dailyFee, coveredTotal, copay, overLimit,
                    itemsJson, (int) nonCovered, previousUnpaid, (int) totalDue);
            return bills.saveAndFlush(bill);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    /** 수납을 더한다. 여러 번에 나눠 내는 경우가 흔하므로 누적하고, 상태는 합계 기준으로 다시 정한다. */
    @Transactional
    public LabCopayBillDto.View addPayment(Long id, LabCopayBillDto.PaymentInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null || input.amount() == null) {
            throw LabValidation.bad("수납 금액을 입력해 주세요");
        }
        int amount = input.amount();
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw LabValidation.bad("수납 금액은 1원 이상 1억 원 이하여야 합니다");
        }
        String payer = LabValidation.text(input.payerName(), 50, "납부자 이름");
        LocalDate paidOn = input.paidOn() != null ? input.paidOn() : LocalDate.now(ZoneId.of("Asia/Seoul"));

        LabCopayBill bill = bills.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("청구서를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        try {
            bill.addPayment(amount, paidOn, payer);
        } catch (ArithmeticException e) {
            throw LabValidation.bad("누적 수납액이 너무 큽니다");
        }
        return toView(bills.saveAndFlush(bill), elders.nameOf(bill.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabCopayBill bill = bills.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("청구서를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        bills.delete(bill);
    }

    private static int amount(int value, String label) {
        if (value < 0) {
            throw LabValidation.bad(label + "은(는) 0원 이상이어야 합니다");
        }
        if (value > MAX_AMOUNT) {
            throw LabValidation.bad(label + "이(가) 너무 큽니다 (최대 1억 원)");
        }
        return value;
    }

    private static List<LabCopayBillDto.NonCoveredItem> cleanItems(List<LabCopayBillDto.NonCoveredItem> raw) {
        List<LabCopayBillDto.NonCoveredItem> items = new ArrayList<>();
        if (raw == null) {
            return items;
        }
        if (raw.size() > MAX_ITEMS) {
            throw LabValidation.bad("비급여 항목은 " + MAX_ITEMS + "개까지 넣을 수 있습니다");
        }
        for (LabCopayBillDto.NonCoveredItem item : raw) {
            if (item == null) {
                throw LabValidation.bad("비급여 항목이 비어 있습니다");
            }
            String label = LabValidation.requiredText(item.label(), MAX_LABEL, "비급여 항목 이름");
            int value = amount(LabValidation.required(item.amount(), "비급여 금액"), "비급여 금액");
            items.add(new LabCopayBillDto.NonCoveredItem(label, value));
        }
        return items;
    }

    private static LabCopayBillDto.View toView(LabCopayBill b, String elderlyName) {
        return new LabCopayBillDto.View(b.getId(), b.getElderlyId(), elderlyName, b.getBillMonth(),
                b.getCareGrade(), b.getCopayClass(), b.getDaycareUnit(), b.getHourBand(), b.getDays(),
                b.getDailyFee(), b.getCoveredTotal(), b.getCopayAmount(), b.getOverLimitAmount(),
                LabJson.readItems(b.getNonCoveredItems()), b.getNonCoveredAmount(), b.getPreviousUnpaid(),
                b.getTotalDue(), b.getPaidAmount(), b.getLastPaidOn(), b.getPayerName(), b.getStatus());
    }
}
