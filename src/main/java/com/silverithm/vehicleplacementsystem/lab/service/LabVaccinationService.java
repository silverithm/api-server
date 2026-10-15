package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabVaccinationDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabVaccination;
import com.silverithm.vehicleplacementsystem.lab.repository.LabVaccinationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예방접종 기록 — 어르신 x 접종 연도 x 백신 1건. 평가 접종률(인플루엔자)은 웹이 이 기록으로 계산한다.
 *
 * <p>접종했으면(DONE) 접종일과 확인 방법이 있어야 하고 접종일은 접종 연도 안·오늘 이전이다. 거부·제외는 사유가
 * 있어야 하고 접종일·확인 방법·기관은 비운다(접종하지 않았는데 접종일이 남으면 접종률이 부풀려진다).
 * 어르신 id는 클라이언트가 보내는 값이라 같은 기관 소속인지 확인하고 아니면 "없음"과 같은 404로 답한다.
 */
@Service
public class LabVaccinationService {

    static final Set<String> VACCINES = Set.of("INFLUENZA", "PNEUMOCOCCAL", "COVID19");
    static final Set<String> STATUSES = Set.of("DONE", "REFUSED", "EXCLUDED", "UNKNOWN");
    static final Set<String> VERIFIED_BY = Set.of("CERT", "GUARDIAN", "NIP", "OTHER");
    private static final int MIN_SEASON = 2020;
    private static final int MAX_SEASON = 2100;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabVaccinationRepository vaccinations;
    private final TransactionTemplate tx;

    public LabVaccinationService(LabAccessService access, LabElderDirectory elders,
                                 LabVaccinationRepository vaccinations, PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.vaccinations = vaccinations;
        this.tx = new TransactionTemplate(txManager);
    }

    /** year 또는 elderlyId 중 하나는 꼭 필요 — 둘 다 주면 그 어르신의 그 해 */
    @Transactional(readOnly = true)
    public List<LabVaccinationDto.View> list(String year, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        boolean hasYear = year != null && !year.isBlank();
        if (!hasYear && elderlyId == null) {
            throw LabValidation.bad("year 또는 elderlyId 중 하나를 보내 주세요");
        }
        Short y = hasYear ? (short) LabValidation.year(year.strip(), "연도") : null;
        List<LabVaccination> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = y == null
                    ? vaccinations.findByCompanyIdAndElderlyIdOrderBySeasonYearDescVaccineAsc(companyId, elderlyId)
                    : vaccinations.findByCompanyIdAndElderlyIdAndSeasonYearOrderByVaccineAsc(companyId, elderlyId, y);
        } else {
            rows = vaccinations.findByCompanyIdAndSeasonYearOrderByElderlyIdAscVaccineAsc(companyId, y);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(r -> view(r, names.getOrDefault(r.getElderlyId(), ""))).toList();
    }

    public LabVaccinationDto.View upsert(LabVaccinationDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        Long companyId = caller.companyId();
        if (input == null) {
            throw LabValidation.bad("예방접종 내용을 보내 주세요");
        }
        if (input.elderlyId() == null) {
            throw LabValidation.bad("어르신을 선택해 주세요");
        }
        int season = LabValidation.required(input.seasonYear(), "접종 연도");
        if (season < MIN_SEASON || season > MAX_SEASON) {
            throw LabValidation.bad("접종 연도는 " + MIN_SEASON + "~" + MAX_SEASON + " 사이여야 합니다");
        }
        String vaccine = LabValidation.oneOf(input.vaccine(), VACCINES, "백신");
        String status = LabValidation.oneOf(input.status(), STATUSES, "접종 상태");
        // 접종일은 상태와 상관없이 값이 있으면 말이 되는 날짜여야 한다(미래·범위 밖은 400)
        LocalDate givenOn = LabValidation.pastOrTodayOrNull(input.givenOn(), "접종일");
        String institution = LabValidation.text(input.institution(), 100, "접종 기관");
        String reason = LabValidation.text(input.reason(), 200, "사유");
        String note = LabValidation.text(input.note(), 200, "메모");
        String verifiedBy = input.verifiedBy() == null || input.verifiedBy().isBlank() ? null
                : LabValidation.oneOf(input.verifiedBy(), VERIFIED_BY, "확인 방법");

        switch (status) {
            case "DONE" -> {
                if (givenOn == null) {
                    throw LabValidation.bad("접종했으면 접종일을 입력해 주세요");
                }
                if (givenOn.getYear() != season) {
                    throw LabValidation.bad("접종일은 접종 연도(" + season + "년) 안이어야 합니다");
                }
                if (verifiedBy == null) {
                    throw LabValidation.bad("접종했으면 확인 방법(접종증명서·보호자 확인·예방접종등록 조회·기타)을 선택해 주세요");
                }
                reason = null;
            }
            case "REFUSED", "EXCLUDED" -> {
                if (reason == null) {
                    throw LabValidation.bad("REFUSED".equals(status) ? "거부한 사유를 입력해 주세요"
                            : "접종 제외 사유를 입력해 주세요");
                }
                givenOn = null;
                verifiedBy = null;
                institution = null;
            }
            default -> {
                givenOn = null;
                verifiedBy = null;
                institution = null;
                reason = null;
            }
        }

        elders.requireInCompany(input.elderlyId(), companyId);
        Long elderlyId = input.elderlyId();
        short seasonYear = (short) season;
        LocalDate finalGivenOn = givenOn;
        String finalInstitution = institution;
        String finalVerifiedBy = verifiedBy;
        String finalReason = reason;
        String writer = LabValidation.writerName(caller);
        LabVaccination saved = LabValidation.upsertWithRetry(tx, () -> {
            LabVaccination row = vaccinations
                    .findByCompanyIdAndElderlyIdAndSeasonYearAndVaccine(companyId, elderlyId, seasonYear, vaccine)
                    .orElseGet(() -> new LabVaccination(companyId, elderlyId, seasonYear, vaccine));
            row.fill(status, finalGivenOn, finalInstitution, finalVerifiedBy, finalReason, note, writer);
            return vaccinations.saveAndFlush(row);
        });
        return view(saved, elders.nameOf(elderlyId));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        vaccinations.delete(vaccinations.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("예방접종 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static LabVaccinationDto.View view(LabVaccination v, String elderlyName) {
        return new LabVaccinationDto.View(v.getId(), v.getElderlyId(), elderlyName, v.getSeasonYear(),
                v.getVaccine(), v.getStatus(), v.getGivenOn(), v.getInstitution(), v.getVerifiedBy(), v.getReason(),
                v.getNote(), v.getWriterName(), v.getUpdatedAt());
    }
}
