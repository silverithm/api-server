package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabPolicyDocDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabPolicyDoc;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCompanyLockRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabPolicyDocRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 규정·지침 대장 — 운영규정·급여제공지침·개인정보 처리방침·감염병 대응계획·이동서비스 수칙·고충처리지침의
 * 개정일·비치 장소·직원 교육일과 항목 점검 결과.
 *
 * <p>원문은 받지도 저장하지도 않는다(항목을 갖췄는지만 checks에 담는다). 같은 종류(급여제공지침은 번호까지)는
 * 기관 안에서 하나다 — 기타(OTHER)만 여러 개. 두 요청이 동시에 "아직 없다"고 보고 둘 다 만드는 것을 막으려고
 * 유일 키 대신 기관 행을 잠그고 확인한다.
 */
@Service
public class LabPolicyDocService {

    static final Set<String> KINDS = Set.of("OPERATING_RULES", "CARE_GUIDE", "PRIVACY_POLICY", "INFECTION_PLAN",
            "TRANSPORT_RULES", "GRIEVANCE_GUIDE", "OTHER");
    static final int MAX_CHECKS = 30;
    static final int MAX_CHECK_KEY = 40;
    private static final Pattern CHECK_KEY = Pattern.compile("^[A-Za-z0-9_]+$");

    private final LabAccessService access;
    private final LabPolicyDocRepository docs;
    private final LabCompanyLockRepository companyLock;

    public LabPolicyDocService(LabAccessService access, LabPolicyDocRepository docs,
                               LabCompanyLockRepository companyLock) {
        this.access = access;
        this.docs = docs;
        this.companyLock = companyLock;
    }

    @Transactional(readOnly = true)
    public List<LabPolicyDocDto.View> list() {
        Long companyId = access.requireCompanyId();
        return docs.findByCompanyIdOrderByKindAscGuideNoAscIdAsc(companyId).stream()
                .map(LabPolicyDocService::view).toList();
    }

    @Transactional
    public LabPolicyDocDto.View create(LabPolicyDocDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabPolicyDoc doc = new LabPolicyDoc(companyId);
        apply(doc, input, companyId);
        return view(docs.saveAndFlush(doc));
    }

    @Transactional
    public LabPolicyDocDto.View update(Long id, LabPolicyDocDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabPolicyDoc doc = docs.findByIdAndCompanyId(id, companyId).orElseThrow(LabPolicyDocService::notFound);
        apply(doc, input, companyId);
        return view(docs.saveAndFlush(doc));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        docs.delete(docs.findByIdAndCompanyId(id, companyId).orElseThrow(LabPolicyDocService::notFound));
    }

    private void apply(LabPolicyDoc doc, LabPolicyDocDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("규정·지침 내용을 보내 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), KINDS, "종류");
        Byte guideNo = guideNo(kind, input.guideNo());
        String title = LabValidation.requiredText(input.title(), 100, "제목");
        LocalDate revisedOn = LabValidation.pastOrTodayOrNull(input.revisedOn(), "개정일");
        String location = LabValidation.text(input.location(), 100, "비치 장소");
        Map<String, Boolean> checks = checks(input.checks());
        LocalDate trainedOn = LabValidation.pastOrTodayOrNull(input.trainedOn(), "직원 교육일");
        String note = LabValidation.text(input.note(), 300, "메모");

        if (!"OTHER".equals(kind)) {
            // 기관 행을 잠가 같은 종류를 동시에 만드는 요청을 줄 세운다. 잠금은 이 트랜잭션이 끝날 때 풀린다
            companyLock.lockById(companyId).orElseThrow(() -> new CustomException("기관을 찾을 수 없습니다",
                    HttpStatus.NOT_FOUND));
            boolean taken = docs.findByCompanyIdAndKind(companyId, kind).stream()
                    .anyMatch(other -> !Objects.equals(other.getId(), doc.getId())
                            && Objects.equals(other.getGuideNo(), guideNo));
            if (taken) {
                throw new CustomException("CARE_GUIDE".equals(kind)
                        ? "급여제공지침 " + guideNo + "번은 이미 대장에 있습니다. 기존 항목을 수정해 주세요"
                        : "같은 종류의 문서가 이미 대장에 있습니다. 기존 항목을 수정해 주세요 (여러 개를 두려면 '기타'를 쓰세요)",
                        HttpStatus.CONFLICT);
            }
        }
        doc.fill(kind, guideNo, title, revisedOn, location, LabJson.write(checks), trainedOn, note);
    }

    /** 급여제공지침만 1~12 번호가 필수. 그 밖의 종류는 번호를 비운다 */
    private static Byte guideNo(String kind, Integer value) {
        if (!"CARE_GUIDE".equals(kind)) {
            return null;
        }
        if (value == null) {
            throw LabValidation.bad("급여제공지침은 번호(1~12)를 입력해 주세요");
        }
        if (value < 1 || value > 12) {
            throw LabValidation.bad("급여제공지침 번호는 1~12 사이여야 합니다");
        }
        return value.byteValue();
    }

    /** 항목 키는 영문·숫자·_ 40자 이내, 30개 이하, 값은 true/false. 입력 순서를 지킨다 */
    private static Map<String, Boolean> checks(Map<String, Boolean> input) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (input == null) {
            return result;
        }
        if (input.size() > MAX_CHECKS) {
            throw LabValidation.bad("점검 항목은 최대 " + MAX_CHECKS + "개까지 입력할 수 있습니다");
        }
        for (Map.Entry<String, Boolean> e : input.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isEmpty() || key.length() > MAX_CHECK_KEY || !CHECK_KEY.matcher(key).matches()) {
                throw LabValidation.bad("점검 항목 키는 영문·숫자·밑줄(_)만 " + MAX_CHECK_KEY + "자까지 쓸 수 있습니다");
            }
            if (e.getValue() == null) {
                throw LabValidation.bad("점검 항목 '" + key + "'은(는) 갖췄는지(true/false)를 선택해 주세요");
            }
            result.put(key, e.getValue());
        }
        return result;
    }

    private static LabPolicyDocDto.View view(LabPolicyDoc d) {
        return new LabPolicyDocDto.View(d.getId(), d.getKind(), d.getGuideNo() == null ? null : d.getGuideNo().intValue(),
                d.getTitle(), d.getRevisedOn(), d.getLocation(), LabJson.readChecks(d.getChecks()), d.getTrainedOn(),
                d.getNote(), d.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("규정·지침을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
