package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.VoiceMessage;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.VoiceMessageRepository;
import com.silverithm.vehicleplacementsystem.service.ApprovalAccessService.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고충·신고 + 건의함 (VoiceBox).
 * 제출은 같은 기관의 인증 사용자 누구나, 열람·처리(상태/답변)는 기관 관리자만.
 * 익명 글은 관리자 응답에서 작성자 정보를 가린다 (DB에는 본인 내역 조회용으로 저장).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceMessageService {

    private final VoiceMessageRepository voiceMessageRepository;
    private final CompanyRepository companyRepository;
    private final ApprovalAccessService accessService;

    public record VoiceMessageDTO(Long id, String type, String title, String content, boolean isAnonymous,
                                  String authorName, String status, String adminReply, String repliedAt,
                                  String createdAt) {
    }

    private VoiceMessageDTO toDTO(VoiceMessage v, boolean maskAnonymous) {
        boolean mask = maskAnonymous && v.isAnonymous();
        return new VoiceMessageDTO(
                v.getId(),
                v.getType().name(),
                v.getTitle(),
                v.getContent(),
                v.isAnonymous(),
                mask ? "익명" : v.getAuthorName(),
                v.getStatus().name(),
                v.getAdminReply(),
                v.getRepliedAt() != null ? v.getRepliedAt().toString() : null,
                v.getCreatedAt() != null ? v.getCreatedAt().toString() : null
        );
    }

    private CallerIdentity requireCaller(UserDetails userDetails) {
        CallerIdentity caller = accessService.resolveCaller(userDetails);
        if (caller == null || caller.companyId() == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }
        return caller;
    }

    @Transactional
    public VoiceMessageDTO create(UserDetails userDetails, String type, String title, String content,
                                  boolean isAnonymous) {
        CallerIdentity caller = requireCaller(userDetails);

        VoiceMessage.VoiceType voiceType;
        try {
            voiceType = VoiceMessage.VoiceType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유형은 GRIEVANCE 또는 SUGGESTION이어야 합니다.");
        }
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("제목과 내용을 입력해주세요.");
        }

        Company company = companyRepository.findById(caller.companyId())
                .orElseThrow(() -> new IllegalArgumentException("소속 기관을 찾을 수 없습니다."));

        VoiceMessage saved = voiceMessageRepository.save(VoiceMessage.builder()
                .company(company)
                .type(voiceType)
                .title(title.trim())
                .content(content)
                .isAnonymous(isAnonymous)
                .authorType(caller.type())
                .authorRefId(caller.refId())
                .authorName(caller.name())
                .status(VoiceMessage.VoiceStatus.RECEIVED)
                .build());

        log.info("[VoiceBox] 접수: id={}, companyId={}, type={}, anonymous={}",
                saved.getId(), company.getId(), voiceType, isAnonymous);
        // 익명 여부와 무관하게 본인 내역으로는 조회 가능; 관리자 화면에서는 익명 처리
        return toDTO(saved, false);
    }

    /** 기관 관리자용 목록 — 익명 글은 작성자를 가린 채 반환 */
    @Transactional(readOnly = true)
    public List<VoiceMessageDTO> listForAdmin(UserDetails userDetails, String type) {
        CallerIdentity caller = requireCaller(userDetails);
        if (!accessService.isCompanyAdmin(caller, caller.companyId())) {
            throw new SecurityException("고충·건의함은 기관 관리자만 열람할 수 있습니다.");
        }

        List<VoiceMessage> list;
        if (type != null && !type.isBlank()) {
            VoiceMessage.VoiceType voiceType = VoiceMessage.VoiceType.valueOf(type.trim().toUpperCase());
            list = voiceMessageRepository.findByCompanyIdAndTypeOrderByCreatedAtDesc(caller.companyId(), voiceType);
        } else {
            list = voiceMessageRepository.findByCompanyIdOrderByCreatedAtDesc(caller.companyId());
        }
        return list.stream().map(v -> toDTO(v, true)).toList();
    }

    /** 본인 제출 내역 — 익명 글도 본인에게는 그대로 보인다 */
    @Transactional(readOnly = true)
    public List<VoiceMessageDTO> listMine(UserDetails userDetails) {
        CallerIdentity caller = requireCaller(userDetails);
        return voiceMessageRepository
                .findByAuthorTypeAndAuthorRefIdOrderByCreatedAtDesc(caller.type(), caller.refId())
                .stream().map(v -> toDTO(v, false)).toList();
    }

    /** 상태 변경·답변 — 기관 관리자만 */
    @Transactional
    public VoiceMessageDTO update(UserDetails userDetails, Long id, String status, String adminReply) {
        CallerIdentity caller = requireCaller(userDetails);
        if (!accessService.isCompanyAdmin(caller, caller.companyId())) {
            throw new SecurityException("고충·건의함은 기관 관리자만 처리할 수 있습니다.");
        }

        VoiceMessage message = voiceMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("항목을 찾을 수 없습니다: " + id));
        if (!message.getCompany().getId().equals(caller.companyId())) {
            throw new SecurityException("다른 기관의 항목입니다.");
        }

        if (status != null && !status.isBlank()) {
            message.setStatus(VoiceMessage.VoiceStatus.valueOf(status.trim().toUpperCase()));
        }
        if (adminReply != null) {
            message.setAdminReply(adminReply.isBlank() ? null : adminReply);
            message.setRepliedAt(adminReply.isBlank() ? null : LocalDateTime.now());
        }

        VoiceMessage saved = voiceMessageRepository.save(message);
        log.info("[VoiceBox] 처리: id={}, status={}, hasReply={}", saved.getId(), saved.getStatus(),
                saved.getAdminReply() != null);
        return toDTO(saved, true);
    }
}
