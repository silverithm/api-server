package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffHrDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffCert;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffProfile;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffCertRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 직원 인사 기준(경력직·보수교육 대상·건강검진 주기의 근거)과 이수·검진 기록.
 *
 * <p>기준은 직원 1명에 한 건이라 PUT이 memberId로 upsert한다. memberId는 클라이언트가 보내는 값이라 다른 기관
 * 직원 id를 넣으면 남의 직원 이름이 응답에 실릴 수 있다 — 모든 경로가 요청자 기관 소속인지 먼저 확인하고,
 * 아니면 존재 여부를 알려 주지 않도록 "없음"과 같은 404로 답한다. 점수·상태 계산은 웹이 하고 서버는 입력이
 * 말이 되는지만 지킨다.
 */
@Service
public class LabStaffHrService {

    /** 급여 기준(LabPayrollService)과 같은 직종 값 */
    static final Set<String> JOB_TYPES = Set.of("CAREGIVER", "SOCIAL_WORKER", "NURSE", "NURSE_AIDE", "THERAPIST",
            "COOK", "NUTRITIONIST", "DRIVER", "SANITATION", "OFFICE", "DIRECTOR", "OTHER");
    static final Set<String> CERT_KINDS = Set.of("CONTINUING_EDU", "HEALTH_CHECK", "OTHER");
    private static final int MIN_BIRTH_YEAR = 1930;
    private static final int MAX_BIRTH_YEAR = 2010;
    private static final BigDecimal MIN_EDU_HOURS = new BigDecimal("0.5");
    private static final BigDecimal MAX_EDU_HOURS = new BigDecimal("40");

    private final LabAccessService access;
    private final LabStaffService staffDirectory;
    private final LabStaffProfileRepository profiles;
    private final LabStaffCertRepository certs;
    private final TransactionTemplate tx;

    public LabStaffHrService(LabAccessService access, LabStaffService staffDirectory,
                             LabStaffProfileRepository profiles, LabStaffCertRepository certs,
                             PlatformTransactionManager txManager) {
        this.access = access;
        this.staffDirectory = staffDirectory;
        this.profiles = profiles;
        this.certs = certs;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- 직원 인사 기준 ----

    /** 기관 전체 기준 — 직원 이름순(이름은 암호화 열이라 DB가 아니라 여기서 정렬) */
    @Transactional(readOnly = true)
    public List<LabStaffHrDto.ProfileView> listProfiles() {
        Long companyId = access.requireCompanyId();
        List<LabStaffProfile> rows = profiles.findByCompanyIdOrderByIdAsc(companyId);
        Map<Long, String> names = staffDirectory.namesOf(
                rows.stream().map(LabStaffProfile::getMemberId).collect(java.util.stream.Collectors.toSet()),
                companyId);
        return rows.stream().map(r -> profileView(r, names))
                .sorted(Comparator.comparing(LabStaffHrDto.ProfileView::memberName)
                        .thenComparing(LabStaffHrDto.ProfileView::id))
                .toList();
    }

    public LabStaffHrDto.ProfileView upsertProfile(LabStaffHrDto.ProfileInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("직원 인사 기준을 보내 주세요");
        }
        Long memberId = requireMember(input.memberId(), companyId);
        LocalDate hireDate = LabValidation.plausibleDate(input.hireDate(), "입사일");
        String jobType = LabValidation.oneOf(input.jobType(), JOB_TYPES, "직종");
        Short birthYear = birthYear(input.birthYear());
        LocalDate licensePassedOn = input.licensePassedOn() == null ? null
                : LabValidation.plausibleDate(input.licensePassedOn(), "자격시험 합격일");
        boolean officeWorker = Boolean.TRUE.equals(input.officeWorker());
        String note = LabValidation.text(input.note(), 200, "메모");

        LabStaffProfile saved = LabValidation.upsertWithRetry(tx, () -> {
            LabStaffProfile row = profiles.findByCompanyIdAndMemberId(companyId, memberId)
                    .orElseGet(() -> new LabStaffProfile(companyId, memberId));
            row.fill(hireDate, jobType, birthYear, licensePassedOn, officeWorker, note);
            return profiles.saveAndFlush(row);
        });
        return profileView(saved, staffDirectory.namesOf(Set.of(memberId), companyId));
    }

    @Transactional
    public void deleteProfile(Long id) {
        Long companyId = access.requireCompanyId();
        profiles.delete(profiles.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("직원 인사 기준을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    // ---- 이수·검진 기록 ----

    /**
     * memberId·year로 거른다 — 둘 다 주면 그 직원의 그 해, 둘 다 없으면 기관 전체. 날짜 최신순.
     * 직원 인사 화면·오늘 확인할 것(건강검진 기한)·평가 근거는 해를 넘겨 가장 최근 검진·교육을 봐야 해서 전체를 부른다.
     */
    @Transactional(readOnly = true)
    public List<LabStaffHrDto.CertView> listCerts(Long memberId, String year) {
        Long companyId = access.requireCompanyId();
        LocalDate from = null;
        LocalDate to = null;
        if (year != null && !year.isBlank()) {
            int y = LabValidation.year(year.strip(), "연도");
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        }
        List<LabStaffCert> rows;
        if (memberId != null) {
            requireMember(memberId, companyId);
            rows = from == null
                    ? certs.findByCompanyIdAndMemberIdOrderByDoneOnDescIdDesc(companyId, memberId)
                    : certs.findByCompanyIdAndMemberIdAndDoneOnBetweenOrderByDoneOnDescIdDesc(companyId, memberId,
                            from, to);
        } else if (from != null) {
            rows = certs.findByCompanyIdAndDoneOnBetweenOrderByDoneOnDescIdDesc(companyId, from, to);
        } else {
            rows = certs.findByCompanyIdOrderByDoneOnDescIdDesc(companyId);
        }
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(r -> ids.add(r.getMemberId()));
        Map<Long, String> names = staffDirectory.namesOf(ids, companyId);
        return rows.stream().map(r -> certView(r, names)).toList();
    }

    @Transactional
    public LabStaffHrDto.CertView createCert(LabStaffHrDto.CertInput input) {
        Long companyId = access.requireCompanyId();
        LabStaffCert cert = new LabStaffCert(companyId);
        String name = applyCert(cert, input, companyId);
        return certView(certs.saveAndFlush(cert), Map.of(cert.getMemberId(), name));
    }

    @Transactional
    public LabStaffHrDto.CertView updateCert(Long id, LabStaffHrDto.CertInput input) {
        Long companyId = access.requireCompanyId();
        LabStaffCert cert = certs.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("이수·검진 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        String name = applyCert(cert, input, companyId);
        return certView(certs.saveAndFlush(cert), Map.of(cert.getMemberId(), name));
    }

    @Transactional
    public void deleteCert(Long id) {
        Long companyId = access.requireCompanyId();
        certs.delete(certs.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("이수·검진 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    /** 검증 후 채우고 직원 이름을 돌려준다 */
    private String applyCert(LabStaffCert cert, LabStaffHrDto.CertInput input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("이수·검진 내용을 보내 주세요");
        }
        Long memberId = requireMember(input.memberId(), companyId);
        String kind = LabValidation.oneOf(input.kind(), CERT_KINDS, "구분");
        LocalDate doneOn = LabValidation.plausibleDate(input.doneOn(), "이수·검진일");
        if (doneOn.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("이수·검진일은 오늘 이후일 수 없습니다. 아직 받지 않은 것은 기록하지 않습니다");
        }
        BigDecimal hours = hours(kind, input.hours());
        String institution = LabValidation.text(input.institution(), 100, "기관");
        String certNo = LabValidation.text(input.certNo(), 40, "증명서 번호");
        String note = LabValidation.text(input.note(), 200, "메모");
        cert.fill(memberId, kind, doneOn, hours, institution, certNo, note);
        return staffDirectory.namesOf(Set.of(memberId), companyId).get(memberId);
    }

    /** 보수교육은 시간이 필수(0.5~40, 소수 첫째 자리). 다른 구분은 비워도 되고, 주면 같은 범위를 지킨다 */
    private static BigDecimal hours(String kind, BigDecimal value) {
        if (value == null) {
            if ("CONTINUING_EDU".equals(kind)) {
                throw LabValidation.bad("보수교육은 이수 시간을 입력해 주세요");
            }
            return null;
        }
        if (value.compareTo(MIN_EDU_HOURS) < 0 || value.compareTo(MAX_EDU_HOURS) > 0) {
            throw LabValidation.bad("이수 시간은 0.5~40 사이여야 합니다");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw LabValidation.bad("이수 시간은 소수 첫째 자리까지 입력할 수 있습니다");
        }
        return value.setScale(1);
    }

    private static Short birthYear(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < MIN_BIRTH_YEAR || value > MAX_BIRTH_YEAR) {
            throw LabValidation.bad("출생연도는 " + MIN_BIRTH_YEAR + "~" + MAX_BIRTH_YEAR + " 사이여야 합니다");
        }
        return value.shortValue();
    }

    /** 같은 기관 직원인지 확인한다 — 아니면 404 */
    private Long requireMember(Long memberId, Long companyId) {
        if (memberId == null) {
            throw LabValidation.bad("직원을 선택해 주세요");
        }
        staffDirectory.requireAllInCompany(List.of(memberId), companyId);
        return memberId;
    }

    private static LabStaffHrDto.ProfileView profileView(LabStaffProfile p, Map<Long, String> names) {
        return new LabStaffHrDto.ProfileView(p.getId(), p.getMemberId(), names.getOrDefault(p.getMemberId(), ""),
                p.getHireDate(), p.getJobType(), p.getBirthYear() == null ? null : p.getBirthYear().intValue(),
                p.getLicensePassedOn(), p.isOfficeWorker(), p.getNote(), p.getUpdatedAt());
    }

    private static LabStaffHrDto.CertView certView(LabStaffCert c, Map<Long, String> names) {
        return new LabStaffHrDto.CertView(c.getId(), c.getMemberId(), names.getOrDefault(c.getMemberId(), ""),
                c.getKind(), c.getDoneOn(), c.getHours(), c.getInstitution(), c.getCertNo(), c.getNote(),
                c.getUpdatedAt());
    }
}
