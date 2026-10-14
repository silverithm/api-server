package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicineDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicineCheck;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicineStock;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicineCheckRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicineStockRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 약품 관리대장과 보관 점검(2026 평가 23).
 *
 * <p>대장은 일반·비상 의약품 목록이고 폐기한 약(disposed_on)은 기본 목록에서 뺀다. 점검은 기관 x 점검일 1건이며 보관함·냉장고
 * 잠금과 약품별 확인·조치(items)를 담는다. 점검 항목의 약품은 모두 같은 기관의 대장에 있어야 하고, 조치가 폐기(DISPOSE)인
 * 약은 서버가 폐기일을 점검일로 채운다(이미 폐기일이 있으면 그대로). 개인정보가 없어 암호화하지 않는다.
 */
@Service
public class LabMedicineService {

    static final int MAX_CHECK_ITEMS = 200;
    private static final int NAME = 60;
    private static final int EFFICACY = 100;
    private static final int UNIT = 10;
    private static final int STOCK_NOTE = 200;
    private static final int ITEM_NOTE = 100;
    private static final int CHECK_NOTE = 500;
    private static final int INSPECTOR = 50;
    private static final long MAX_QUANTITY = 100_000;
    private static final Set<String> STORAGES = Set.of("ROOM", "FRIDGE");
    private static final Set<String> ACTIONS = Set.of("NONE", "DISPOSE", "REORDER", "MOVE");

    private final LabAccessService access;
    private final LabMedicineStockRepository stock;
    private final LabMedicineCheckRepository checks;
    private final TransactionTemplate tx;

    public LabMedicineService(LabAccessService access, LabMedicineStockRepository stock,
                              LabMedicineCheckRepository checks, PlatformTransactionManager txManager) {
        this.access = access;
        this.stock = stock;
        this.checks = checks;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- 약품 관리대장 ----

    /** 사용기한 순. includeDisposed가 true일 때만 폐기한 약도 포함한다 */
    @Transactional(readOnly = true)
    public List<LabMedicineDto.StockView> listStock(boolean includeDisposed) {
        Long companyId = access.requireCompanyId();
        List<LabMedicineStock> rows = includeDisposed
                ? stock.findByCompanyIdOrderByExpiresOnAscIdAsc(companyId)
                : stock.findByCompanyIdAndDisposedOnIsNullOrderByExpiresOnAscIdAsc(companyId);
        return rows.stream().map(LabMedicineService::toView).toList();
    }

    @Transactional
    public LabMedicineDto.StockView createStock(LabMedicineDto.StockInput input) {
        Long companyId = access.requireCompanyId();
        LabMedicineStock item = new LabMedicineStock(companyId);
        applyStock(item, input);
        return toView(stock.saveAndFlush(item));
    }

    @Transactional
    public LabMedicineDto.StockView updateStock(Long id, LabMedicineDto.StockInput input) {
        Long companyId = access.requireCompanyId();
        LabMedicineStock item = stock.findByIdAndCompanyId(id, companyId).orElseThrow(LabMedicineService::stockNotFound);
        applyStock(item, input);
        return toView(stock.saveAndFlush(item));
    }

    @Transactional
    public void deleteStock(Long id) {
        Long companyId = access.requireCompanyId();
        stock.delete(stock.findByIdAndCompanyId(id, companyId).orElseThrow(LabMedicineService::stockNotFound));
    }

    private void applyStock(LabMedicineStock item, LabMedicineDto.StockInput input) {
        if (input == null) {
            throw LabValidation.bad("약품 내용을 보내 주세요");
        }
        String name = LabValidation.requiredText(input.name(), NAME, "약품 이름");
        String efficacy = LabValidation.requiredText(input.efficacy(), EFFICACY, "효능");
        long quantity = LabValidation.longInRange(input.quantity() == null ? null : input.quantity().longValue(), 0,
                MAX_QUANTITY, "수량");
        String unit = LabValidation.requiredText(input.unit(), UNIT, "단위");
        String storage = LabValidation.oneOf(input.storage(), STORAGES, "보관 방법");
        LocalDate expiresOn = LabValidation.plausibleDate(input.expiresOn(), "사용기한");
        LocalDate openedOn = input.openedOn() == null ? null : LabValidation.plausibleDate(input.openedOn(), "개봉일");
        LocalDate disposedOn = input.disposedOn() == null ? null
                : LabValidation.plausibleDate(input.disposedOn(), "폐기일");
        item.fill(name, efficacy, (int) quantity, unit, storage, expiresOn, openedOn, disposedOn,
                LabValidation.text(input.note(), STOCK_NOTE, "메모"));
    }

    // ---- 약품 보관 점검 ----

    /** 그 해 점검(1월 1일~12월 31일), 점검일 오름차순 — 분기별 점검 확인용 */
    @Transactional(readOnly = true)
    public List<LabMedicineDto.CheckView> listChecks(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        return checks.findByCompanyIdAndCheckedOnBetweenOrderByCheckedOnAscIdAsc(companyId, LocalDate.of(y, 1, 1),
                LocalDate.of(y, 12, 31)).stream().map(LabMedicineService::toView).toList();
    }

    /** checkedOn으로 덮어쓴다. 항목의 약품이 같은 기관이 아니면 404, DISPOSE 약은 폐기일을 채운다 */
    public LabMedicineDto.CheckView upsertCheck(LabMedicineDto.CheckInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("점검 내용을 보내 주세요");
        }
        LocalDate day = LabValidation.plausibleDate(input.checkedOn(), "점검일");
        String inspector = LabValidation.requiredText(input.inspectorName(), INSPECTOR, "점검자 이름");
        if (input.cabinetLocked() == null) {
            throw LabValidation.bad("보관함 잠금 여부를 선택해 주세요");
        }
        List<LabMedicineDto.CheckItem> items = items(input.items());
        String note = LabValidation.text(input.note(), CHECK_NOTE, "점검 메모");

        LabMedicineCheck saved = LabValidation.upsertWithRetry(tx, () -> {
            Set<Long> ids = items.stream().map(LabMedicineDto.CheckItem::stockId).collect(Collectors.toSet());
            // 한 번에 읽어 같은 기관 약품인지 확인한다 — 다른 기관 id를 넣어 남의 약품을 폐기 처리하는 길을 막는다
            Map<Long, LabMedicineStock> found = ids.isEmpty() ? Map.of()
                    : stock.findByCompanyIdAndIdIn(companyId, ids).stream()
                            .collect(Collectors.toMap(LabMedicineStock::getId, Function.identity()));
            if (found.size() != ids.size()) {
                throw stockNotFound();
            }
            for (LabMedicineDto.CheckItem item : items) {
                if ("DISPOSE".equals(item.action())) {
                    found.get(item.stockId()).disposeOn(day);
                }
            }
            LabMedicineCheck check = checks.findByCompanyIdAndCheckedOn(companyId, day)
                    .orElseGet(() -> new LabMedicineCheck(companyId, day));
            check.fill(inspector, input.cabinetLocked(), input.fridgeLocked(), LabJson.write(items), note);
            return checks.saveAndFlush(check);
        });
        return toView(saved);
    }

    @Transactional
    public void deleteCheck(Long id) {
        Long companyId = access.requireCompanyId();
        checks.delete(checks.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("약품 점검 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    /** 항목 검증 — 200개 이하, 약품 중복 없음, 확인 여부 필수, 조치 허용 값(비면 NONE) */
    private static List<LabMedicineDto.CheckItem> items(List<LabMedicineDto.CheckItem> input) {
        if (input == null) {
            throw LabValidation.bad("점검 항목을 보내 주세요");
        }
        if (input.size() > MAX_CHECK_ITEMS) {
            throw LabValidation.bad("점검 항목은 최대 " + MAX_CHECK_ITEMS + "개까지 쓸 수 있습니다");
        }
        Set<Long> seen = new HashSet<>();
        List<LabMedicineDto.CheckItem> result = new ArrayList<>(input.size());
        for (LabMedicineDto.CheckItem item : input) {
            if (item == null || item.stockId() == null) {
                throw LabValidation.bad("점검 항목에 약품을 지정해 주세요");
            }
            if (!seen.add(item.stockId())) {
                throw LabValidation.bad("같은 약품이 점검 항목에 두 번 들어 있습니다");
            }
            if (item.ok() == null) {
                throw LabValidation.bad("약품별 이상 없음 여부를 선택해 주세요");
            }
            String action = item.action() == null || item.action().isBlank() ? "NONE"
                    : LabValidation.oneOf(item.action(), ACTIONS, "조치");
            result.add(new LabMedicineDto.CheckItem(item.stockId(), item.ok(), action,
                    LabValidation.text(item.note(), ITEM_NOTE, "항목 메모")));
        }
        return result;
    }

    private static LabMedicineDto.StockView toView(LabMedicineStock s) {
        return new LabMedicineDto.StockView(s.getId(), s.getName(), s.getEfficacy(), s.getQuantity(), s.getUnit(),
                s.getStorage(), s.getExpiresOn(), s.getOpenedOn(), s.getDisposedOn(), s.getNote(), s.getUpdatedAt());
    }

    private static LabMedicineDto.CheckView toView(LabMedicineCheck c) {
        return new LabMedicineDto.CheckView(c.getId(), c.getCheckedOn(), c.getInspectorName(), c.isCabinetLocked(),
                c.getFridgeLocked(), LabJson.readMedicineCheckItems(c.getItems()), c.getNote(), c.getUpdatedAt());
    }

    private static CustomException stockNotFound() {
        return new CustomException("약품을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
