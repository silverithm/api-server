package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareRecordDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareRecord;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareRecordRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 급여제공기록 — 어르신·날짜당 1건 저장(upsert), 날짜별/어르신 월별 조회, 삭제.
 *
 * <p>12차의 서식 칸(form)은 계약의 키·범위만 받아 정리한 JSON으로 암호화해 저장한다. 영역별 제공 직원은
 * 같은 기관 직원이어야 하고(아니면 404), 응답에는 직원 이름을 붙인다. 옛 기록은 form이 null이다.
 */
@Service
public class LabCareRecordService {

    private static final int LONG_TEXT = 2000;
    private static final int SHORT_TEXT = 200;
    private static final int NAME = 50;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabStaffService staff;
    private final LabCareRecordRepository records;
    private final TransactionTemplate tx;

    public LabCareRecordService(LabAccessService access, LabElderDirectory elders, LabStaffService staff,
                                LabCareRecordRepository records, PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.staff = staff;
        this.records = records;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 하루치 — 그날 기록이 있는 어르신들의 기록 */
    @Transactional(readOnly = true)
    public List<LabCareRecordDto.View> listByDate(String date) {
        Long companyId = access.requireCompanyId();
        LocalDate day = LabValidation.date(date, "날짜");
        Map<Long, String> names = elders.namesInCompany(companyId);
        List<LabCareRecord> rows = records.findByCompanyIdAndRecordDate(companyId, day);
        Map<Long, String> staffNames = staffNames(rows, companyId);
        return rows.stream()
                .map(r -> toView(r, names.getOrDefault(r.getElderlyId(), ""), staffNames))
                .sorted(java.util.Comparator.comparing(LabCareRecordDto.View::elderlyName))
                .toList();
    }

    /** 어르신 1명의 한 달치 — 날짜 오름차순(월별 출력용) */
    @Transactional(readOnly = true)
    public List<LabCareRecordDto.View> listByElderAndMonth(Long elderlyId, String month) {
        Long companyId = access.requireCompanyId();
        YearMonth ym = LabValidation.yearMonth(month, "월");
        elders.requireInCompany(elderlyId, companyId);
        String name = elders.nameOf(elderlyId);
        List<LabCareRecord> rows = records.findByCompanyIdAndElderlyIdAndRecordDateBetweenOrderByRecordDateAsc(
                companyId, elderlyId, ym.atDay(1), ym.atEndOfMonth());
        Map<Long, String> staffNames = staffNames(rows, companyId);
        return rows.stream().map(r -> toView(r, name, staffNames)).toList();
    }

    public LabCareRecordDto.View upsert(LabCareRecordDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("기록 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate day = LabValidation.required(input.recordDate(), "기록 날짜");

        String physical = LabValidation.text(input.physical(), LONG_TEXT, "신체활동지원");
        String cognitive = LabValidation.text(input.cognitive(), LONG_TEXT, "인지활동");
        String nursing = LabValidation.text(input.nursing(), LONG_TEXT, "건강·간호");
        String training = LabValidation.text(input.training(), LONG_TEXT, "기능회복훈련");
        String specialNote = LabValidation.text(input.specialNote(), LONG_TEXT, "특이사항");
        String rawNote = LabValidation.text(input.rawNote(), LONG_TEXT, "원문 메모");
        String meal = LabValidation.text(input.meal(), SHORT_TEXT, "식사");
        String toilet = LabValidation.text(input.toilet(), SHORT_TEXT, "배설");
        String bath = LabValidation.text(input.bath(), SHORT_TEXT, "목욕");
        String provider = LabValidation.text(input.providerName(), NAME, "제공자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());
        // form이 없으면 기존 값을 유지하지 않고 null로 저장한다 — 웹은 늘 현재 값을 보낸다
        LabCareRecordDto.Form form = input.form() == null ? null : LabCareForm.normalize(input.form());
        String formJson = form == null ? null : LabJson.write(form);
        // 모양이 맞은 뒤에 제공 직원이 이 기관 직원인지 본다 — 한 명이라도 아니면 아무것도 저장하지 않는다
        Map<Long, String> writerNames = form == null ? Map.of()
                : staff.requireAllInCompany(LabCareForm.writerIds(form), companyId);

        LabCareRecord saved = LabValidation.upsertWithRetry(tx, () -> {
            LabCareRecord record = records.findByElderlyIdAndRecordDate(input.elderlyId(), day)
                    .orElseGet(() -> new LabCareRecord(companyId, input.elderlyId(), day));
            record.fill(physical, cognitive, nursing, training, specialNote, rawNote, meal, toilet, bath,
                    provider, ai, formJson);
            return records.saveAndFlush(record);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()), writerNames);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabCareRecord record = records.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        records.delete(record);
    }

    /** 목록의 모든 서식 칸에 적힌 제공 직원 이름을 한 번의 조회로 읽는다 */
    private Map<Long, String> staffNames(List<LabCareRecord> rows, Long companyId) {
        Set<Long> ids = new HashSet<>();
        for (LabCareRecord r : rows) {
            LabCareRecordDto.Form form = LabJson.readCareForm(r.getForm());
            if (form != null) {
                ids.addAll(LabCareForm.writerIds(form));
            }
        }
        return staff.namesOf(ids, companyId);
    }

    private static LabCareRecordDto.View toView(LabCareRecord r, String elderlyName, Map<Long, String> staffNames) {
        LabCareRecordDto.Form stored = LabJson.readCareForm(r.getForm());
        LabCareRecordDto.Form form = stored == null ? null : LabCareForm.withNames(stored, staffNames);
        return new LabCareRecordDto.View(r.getId(), r.getElderlyId(), elderlyName, r.getRecordDate(),
                r.getPhysical(), r.getCognitive(), r.getMeal(), r.getToilet(), r.getBath(), r.getNursing(),
                r.getTraining(), r.getSpecialNote(), r.getRawNote(), r.getProviderName(), r.isAiAssisted(),
                r.getUpdatedAt(), form);
    }
}
