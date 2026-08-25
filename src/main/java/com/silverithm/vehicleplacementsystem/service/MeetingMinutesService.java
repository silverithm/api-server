package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.CreateMeetingMinutesRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.MeetingMinutesAudioChunkRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.MeetingMinutesDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutes;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAttachment;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAttendee;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAudioChunk;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesTemplate;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Position;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MeetingMinutesRepository;
import com.silverithm.vehicleplacementsystem.repository.MeetingMinutesTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.PositionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.service.ApprovalAccessService.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 회의록: 작성 → 등록(참석자 알림) → 병렬 서명 → 완료(결재함 등록).
 *
 * <p>서명은 결재선처럼 순차가 아니라 참석자 각자가 아무 순서로나 한다.
 * 앱이 없는 참석자(외부인 등)는 관리자 화면에서 입회 서명으로 받는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MeetingMinutesService {

    /** 양식 행이 없는 기관이 쓰는 기본 섹션 구성 */
    public static final String DEFAULT_SECTIONS_JSON =
            "[{\"key\":\"all\",\"label\":\"전체\"},"
                    + "{\"key\":\"teams\",\"label\":\"팀별 전달사항\"},"
                    + "{\"key\":\"elders\",\"label\":\"어르신 특이사항\"}]";

    private final MeetingMinutesRepository minutesRepository;
    private final MeetingMinutesTemplateRepository templateRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final ApprovalAccessService accessService;
    private final ResourceScopeGuard resourceScopeGuard;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final MeetingMinutesApprovalBridgeService bridgeService;

    @Transactional(readOnly = true)
    public List<MeetingMinutesDTO> list(Long companyId, UserDetails userDetails) {
        Company company = requireCompany(companyId);
        CallerIdentity caller = requireCaller(userDetails);

        boolean isAdmin = accessService.isCompanyAdmin(caller, company.getId());
        return minutesRepository.findByCompanyIdOrderByMeetingStartAtDesc(company.getId()).stream()
                .filter(minutes -> isAdmin || canView(caller, minutes))
                .map(MeetingMinutesDTO::summaryOf)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingMinutesDTO get(Long id, UserDetails userDetails) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireCanView(caller, minutes);
        return toDetailDTO(minutes);
    }

    public MeetingMinutesDTO create(Long companyId, UserDetails userDetails, CreateMeetingMinutesRequestDTO dto) {
        Company company = requireCompany(companyId);
        CallerIdentity caller = requireCaller(userDetails);

        MeetingMinutes minutes = MeetingMinutes.builder()
                .company(company)
                .title(dto.getTitle().trim())
                .location(blankToNull(dto.getLocation()))
                .authorType(caller.type())
                .authorRefId(caller.refId())
                .authorName(caller.name())
                .meetingStartAt(dto.getMeetingStartAt())
                .meetingEndAt(dto.getMeetingEndAt())
                .sectionsJson(dto.getSectionsJson())
                .rawNotes(dto.getRawNotes())
                .build();

        applyAttendees(minutes, company, dto.getAttendees());
        applyAttachments(minutes, dto.getAttachments());

        MeetingMinutes saved = minutesRepository.save(minutes);
        log.info("[MeetingMinutes] 생성: id={}, title={}, attendees={}", saved.getId(), saved.getTitle(),
                saved.getAttendees().size());
        return toDetailDTO(saved);
    }

    /** 완료 전까지 작성자·관리자가 내용을 고칠 수 있다 */
    public MeetingMinutesDTO update(Long id, UserDetails userDetails, CreateMeetingMinutesRequestDTO dto) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);
        requireNotCompleted(minutes);

        minutes.setTitle(dto.getTitle().trim());
        minutes.setLocation(blankToNull(dto.getLocation()));
        minutes.setMeetingStartAt(dto.getMeetingStartAt());
        minutes.setMeetingEndAt(dto.getMeetingEndAt());
        minutes.setSectionsJson(dto.getSectionsJson());
        minutes.setRawNotes(dto.getRawNotes());

        // 참석자는 서명 기록을 지키면서 갈아끼운다 — 이미 서명한 사람이 새 목록에도 있으면 서명 유지
        replaceAttendeesKeepingSignatures(minutes, dto.getAttendees());

        minutes.getAttachments().clear();
        applyAttachments(minutes, dto.getAttachments());

        return toDetailDTO(minutesRepository.save(minutes));
    }

    /** 등록: 참석자에게 푸시가 나가고 서명 수집이 시작된다 */
    public MeetingMinutesDTO register(Long id, UserDetails userDetails) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);
        requireNotCompleted(minutes);

        if (minutes.getAttendees().isEmpty()) {
            throw new IllegalArgumentException("참석자를 한 명 이상 지정해야 등록할 수 있습니다");
        }

        minutes.setStatus(MeetingMinutes.Status.REGISTERED);
        MeetingMinutes saved = minutesRepository.save(minutes);

        notifyAttendees(saved, false);
        log.info("[MeetingMinutes] 등록: id={}, attendees={}", saved.getId(), saved.getAttendees().size());
        return toDetailDTO(saved);
    }

    /** 미서명 참석자에게만 다시 알린다 */
    public MeetingMinutesDTO remind(Long id, UserDetails userDetails) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);

        if (minutes.getStatus() != MeetingMinutes.Status.REGISTERED) {
            throw new IllegalStateException("서명 수집 중인 회의록만 재알림할 수 있습니다");
        }

        notifyAttendees(minutes, true);
        return toDetailDTO(minutesRepository.save(minutes));
    }

    /** 참석자 본인 서명. 서명 이미지가 없으면 등록 서명을 쓴다 (결재 승인과 같은 계약) */
    public MeetingMinutesDTO signSelf(Long id, Long attendeeId, UserDetails userDetails, String signatureBase64) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        MeetingMinutesAttendee attendee = requireAttendee(minutes, attendeeId);

        if (minutes.getStatus() != MeetingMinutes.Status.REGISTERED) {
            throw new IllegalStateException("등록된 회의록만 서명할 수 있습니다");
        }

        ApprovalStep.ApproverType callerType = caller.type();
        boolean isSelf = attendee.getRefId() != null
                && attendee.getAttendeeType().name().equals(callerType.name())
                && attendee.getRefId().equals(caller.refId());
        if (!isSelf) {
            throw new SecurityException("본인의 서명란에만 서명할 수 있습니다");
        }

        applySignature(attendee, resolveSignature(caller, signatureBase64));
        log.info("[MeetingMinutes] 본인 서명: minutesId={}, attendee={}", id, attendee.getAttendeeName());
        return toDetailDTO(minutesRepository.save(minutes));
    }

    /**
     * 입회 서명 — 회의 자리에서 관리자(또는 작성자) 화면에 참석자가 직접 그린다.
     * 외부 참석자는 이 방법뿐이고, 내부 참석자도 앱을 못 쓰는 동안 이 길로 서명할 수 있다.
     * 등록 서명 대체가 없으므로 서명 이미지가 필수다.
     */
    public MeetingMinutesDTO guestSign(Long id, Long attendeeId, UserDetails userDetails, String signatureBase64) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);
        MeetingMinutesAttendee attendee = requireAttendee(minutes, attendeeId);

        if (minutes.getStatus() != MeetingMinutes.Status.REGISTERED) {
            throw new IllegalStateException("등록된 회의록만 서명할 수 있습니다");
        }
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            throw new IllegalArgumentException("입회 서명은 서명 이미지가 필요합니다");
        }

        applySignature(attendee, storeSignatureImageQuietly(signatureBase64));
        log.info("[MeetingMinutes] 입회 서명: minutesId={}, attendee={}, by={}",
                id, attendee.getAttendeeName(), caller.name());
        return toDetailDTO(minutesRepository.save(minutes));
    }

    /** 완료: 결재함에 완결 문서로 등록된다. 미서명자가 있어도(불참 등) 작성자가 완료할 수 있다 */
    public MeetingMinutesDTO complete(Long id, UserDetails userDetails) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);

        if (minutes.getStatus() == MeetingMinutes.Status.COMPLETED) {
            return toDetailDTO(minutes);   // 멱등 — 두 번 눌러도 문서가 두 개 생기지 않는다
        }

        bridgeService.registerToApprovalBox(minutes);
        minutes.setStatus(MeetingMinutes.Status.COMPLETED);
        minutes.setCompletedAt(LocalDateTime.now());

        MeetingMinutes saved = minutesRepository.save(minutes);
        log.info("[MeetingMinutes] 완료: id={}, approvalRequestId={}",
                saved.getId(), saved.getApprovalRequest() != null ? saved.getApprovalRequest().getId() : null);
        return toDetailDTO(saved);
    }

    public void delete(Long id, UserDetails userDetails) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);
        // 완료된 회의록은 결재함 기록과 짝이라 지우지 않는다 — 문서 관리는 결재함에서
        requireNotCompleted(minutes);
        minutesRepository.delete(minutes);
        log.info("[MeetingMinutes] 삭제: id={}, by={}", id, caller.name());
    }

    /** 실시간 전사문 주기 저장 — 클라이언트가 누적 전문을 보내므로 통째로 갈아끼운다 */
    public void saveTranscript(Long id, UserDetails userDetails, String transcript) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);
        requireNotCompleted(minutes);
        minutes.setTranscript(transcript);
        minutesRepository.save(minutes);
    }

    /** 녹음 조각 등록 (파일은 /files/upload category=meetings로 먼저 올라와 있다) */
    public void addAudioChunk(Long id, UserDetails userDetails, MeetingMinutesAudioChunkRequestDTO dto) {
        MeetingMinutes minutes = requireMinutes(id);
        CallerIdentity caller = requireCaller(userDetails);
        requireAuthorOrAdmin(caller, minutes);
        requireNotCompleted(minutes);

        minutes.getAudioChunks().add(MeetingMinutesAudioChunk.builder()
                .meetingMinutes(minutes)
                .seq(dto.getSeq())
                .fileUrl(dto.getFilePath())
                .durationSec(dto.getDurationSec())
                .build());
        minutesRepository.save(minutes);
    }

    // ---- 양식 (기관별 섹션 구성) ----

    @Transactional(readOnly = true)
    public Map<String, String> getTemplate(Long companyId, UserDetails userDetails) {
        Company company = requireCompany(companyId);
        String sections = templateRepository.findByCompanyId(company.getId())
                .map(MeetingMinutesTemplate::getSections)
                .orElse(DEFAULT_SECTIONS_JSON);
        return Map.of("sections", sections);
    }

    public Map<String, String> saveTemplate(Long companyId, UserDetails userDetails, String sectionsJson) {
        Company company = requireCompany(companyId);
        CallerIdentity caller = requireCaller(userDetails);
        if (!accessService.isCompanyAdmin(caller, company.getId())) {
            throw new SecurityException("양식은 관리자만 수정할 수 있습니다");
        }

        MeetingMinutesTemplate template = templateRepository.findByCompanyId(company.getId())
                .orElseGet(() -> MeetingMinutesTemplate.builder().company(company).build());
        template.setSections(sectionsJson);
        templateRepository.save(template);
        return Map.of("sections", sectionsJson);
    }

    // ---- 내부 도우미 ----

    /**
     * 참석자 입력을 실제 사람으로 풀어 넣는다.
     * POSITION은 그 직책의 재직 직원 전원으로 펼치고, 같은 사람이 두 번 들어오면 하나로 줄인다.
     */
    private void applyAttendees(MeetingMinutes minutes, Company company,
                                List<CreateMeetingMinutesRequestDTO.AttendeeEntry> entries) {
        // 회의 시작(녹음) 시점엔 참석자를 아직 안 골랐을 수 있다 — 필수 검증은 등록 시점에 한다
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (CreateMeetingMinutesRequestDTO.AttendeeEntry entry : entries) {
            String type = entry.getAttendeeType();
            if ("EXTERNAL".equals(type)) {
                String name = blankToNull(entry.getName());
                if (name == null) {
                    throw new IllegalArgumentException("외부 참석자는 이름이 필요합니다");
                }
                if (seen.add("EXTERNAL:" + name)) {
                    minutes.getAttendees().add(attendeeOf(minutes,
                            MeetingMinutesAttendee.AttendeeType.EXTERNAL, null, name));
                }
                continue;
            }

            if ("POSITION".equals(type)) {
                Position position = positionRepository.findById(entry.getRefId())
                        .orElseThrow(() -> new IllegalArgumentException("직책을 찾을 수 없습니다: " + entry.getRefId()));
                if (position.getCompany() == null || !company.getId().equals(position.getCompany().getId())) {
                    throw new IllegalArgumentException("다른 회사의 직책은 지정할 수 없습니다: " + position.getName());
                }
                for (Member member : memberRepository.findByPositionEntity(position)) {
                    if (member.getStatus() == Member.MemberStatus.ACTIVE
                            && seen.add("MEMBER:" + member.getId())) {
                        minutes.getAttendees().add(attendeeOf(minutes,
                                MeetingMinutesAttendee.AttendeeType.MEMBER, member.getId(), member.getName()));
                    }
                }
                continue;
            }

            if ("ADMIN".equals(type)) {
                AppUser appUser = userRepository.findById(entry.getRefId())
                        .orElseThrow(() -> new IllegalArgumentException("관리자를 찾을 수 없습니다: " + entry.getRefId()));
                if (appUser.getCompany() == null || !company.getId().equals(appUser.getCompany().getId())) {
                    throw new IllegalArgumentException("다른 회사의 관리자는 지정할 수 없습니다");
                }
                if (seen.add("ADMIN:" + appUser.getId())) {
                    minutes.getAttendees().add(attendeeOf(minutes,
                            MeetingMinutesAttendee.AttendeeType.ADMIN, appUser.getId(), appUser.getUsername()));
                }
                continue;
            }

            Member member = memberRepository.findById(entry.getRefId())
                    .orElseThrow(() -> new IllegalArgumentException("직원을 찾을 수 없습니다: " + entry.getRefId()));
            if (member.getCompany() == null || !company.getId().equals(member.getCompany().getId())) {
                throw new IllegalArgumentException("다른 회사의 직원은 지정할 수 없습니다: " + member.getName());
            }
            if (seen.add("MEMBER:" + member.getId())) {
                minutes.getAttendees().add(attendeeOf(minutes,
                        MeetingMinutesAttendee.AttendeeType.MEMBER, member.getId(), member.getName()));
            }
        }
    }

    private void replaceAttendeesKeepingSignatures(MeetingMinutes minutes,
                                                   List<CreateMeetingMinutesRequestDTO.AttendeeEntry> entries) {
        List<MeetingMinutesAttendee> previous = new ArrayList<>(minutes.getAttendees());
        minutes.getAttendees().clear();
        applyAttendees(minutes, minutes.getCompany(), entries);

        for (MeetingMinutesAttendee attendee : minutes.getAttendees()) {
            previous.stream()
                    .filter(old -> old.isSigned() && sameAttendee(old, attendee))
                    .findFirst()
                    .ifPresent(old -> {
                        attendee.setSignatureUrl(old.getSignatureUrl());
                        attendee.setSignedAt(old.getSignedAt());
                        attendee.setNotifiedAt(old.getNotifiedAt());
                        attendee.setRemindedAt(old.getRemindedAt());
                    });
        }
    }

    private boolean sameAttendee(MeetingMinutesAttendee a, MeetingMinutesAttendee b) {
        if (a.getAttendeeType() != b.getAttendeeType()) {
            return false;
        }
        if (a.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.EXTERNAL) {
            return a.getAttendeeName().equals(b.getAttendeeName());
        }
        return a.getRefId() != null && a.getRefId().equals(b.getRefId());
    }

    private MeetingMinutesAttendee attendeeOf(MeetingMinutes minutes,
                                              MeetingMinutesAttendee.AttendeeType type, Long refId, String name) {
        return MeetingMinutesAttendee.builder()
                .meetingMinutes(minutes)
                .attendeeType(type)
                .refId(refId)
                .attendeeName(name)
                .build();
    }

    private void applyAttachments(MeetingMinutes minutes,
                                  List<CreateMeetingMinutesRequestDTO.AttachmentEntry> entries) {
        if (entries == null) {
            return;
        }
        int order = 0;
        for (CreateMeetingMinutesRequestDTO.AttachmentEntry entry : entries) {
            minutes.getAttachments().add(MeetingMinutesAttachment.builder()
                    .meetingMinutes(minutes)
                    .fileUrl(entry.getFileUrl())
                    .fileName(entry.getFileName())
                    .fileSize(entry.getFileSize())
                    .sortOrder(order++)
                    .build());
        }
    }

    /**
     * 참석자 알림. 한 명 실패가 나머지를 멈추지 않는다 (NoticeService의 수신자별 루프 패턴).
     * remindOnly면 아직 서명하지 않은 사람에게만 간다.
     */
    private void notifyAttendees(MeetingMinutes minutes, boolean remindOnly) {
        LocalDateTime now = LocalDateTime.now();
        for (MeetingMinutesAttendee attendee : minutes.getAttendees()) {
            if (attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.EXTERNAL) {
                continue;
            }
            if (remindOnly && attendee.isSigned()) {
                continue;
            }
            // 작성자 본인에게는 굳이 알리지 않는다
            if (attendee.getRefId() != null
                    && attendee.getAttendeeType().name().equals(minutes.getAuthorType().name())
                    && attendee.getRefId().equals(minutes.getAuthorRefId())) {
                continue;
            }

            try {
                String token = null;
                String recipientUserId = null;
                if (attendee.getAttendeeType() == MeetingMinutesAttendee.AttendeeType.ADMIN) {
                    AppUser appUser = userRepository.findById(attendee.getRefId()).orElse(null);
                    if (appUser != null) {
                        token = appUser.getFcmToken();
                        recipientUserId = String.valueOf(appUser.getId());
                    }
                } else {
                    Member member = memberRepository.findById(attendee.getRefId()).orElse(null);
                    if (member != null) {
                        token = member.getFcmToken();
                        recipientUserId = String.valueOf(member.getId());
                    }
                }

                if (token == null || token.isEmpty()) {
                    log.debug("[MeetingMinutes] FCM 토큰 없음: attendee={}", attendee.getAttendeeName());
                } else {
                    notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                            .recipientToken(token)
                            .title(remindOnly ? "회의록 서명 재요청" : "회의록 서명 요청")
                            .message("'" + minutes.getTitle() + "' 회의록이 등록되었습니다. 내용을 확인하고 서명해 주세요.")
                            .recipientUserId(recipientUserId)
                            .recipientUserName(attendee.getAttendeeName())
                            .type("meeting_minutes")
                            .relatedEntityId(minutes.getId())
                            .relatedEntityType("meeting_minutes")
                            .data(Map.of(
                                    "type", "meeting_minutes",
                                    "minutesId", String.valueOf(minutes.getId())
                            ))
                            .build());
                }

                if (remindOnly) {
                    attendee.setRemindedAt(now);
                } else {
                    attendee.setNotifiedAt(now);
                }
            } catch (Exception e) {
                log.error("[MeetingMinutes] 참석자 알림 실패: minutesId={}, attendee={}, {}",
                        minutes.getId(), attendee.getAttendeeName(), e.getMessage());
            }
        }
    }

    private void applySignature(MeetingMinutesAttendee attendee, String signatureUrl) {
        attendee.setSignatureUrl(signatureUrl);
        attendee.setSignedAt(LocalDateTime.now());
    }

    /** 즉석 서명이 있으면 저장, 없거나 실패하면 등록 서명 (ApprovalRequestService.resolveSignature와 동일) */
    private String resolveSignature(CallerIdentity caller, String signatureBase64) {
        if (signatureBase64 != null && !signatureBase64.isBlank()) {
            try {
                return storeSignatureImage(signatureBase64);
            } catch (Exception e) {
                log.error("[MeetingMinutes] 즉석 서명 저장 실패, 등록 서명으로 대체: {}", e.getMessage());
            }
        }
        return accessService.findRegisteredSignature(caller);
    }

    private String storeSignatureImageQuietly(String base64) {
        try {
            return storeSignatureImage(base64);
        } catch (IOException e) {
            throw new IllegalArgumentException("서명 이미지를 저장하지 못했습니다: " + e.getMessage());
        }
    }

    private String storeSignatureImage(String base64) throws IOException {
        String payload = base64.trim();
        int commaIndex = payload.indexOf(',');
        if (payload.startsWith("data:") && commaIndex > 0) {
            payload = payload.substring(commaIndex + 1);
        }

        byte[] bytes = Base64.getDecoder().decode(payload);

        // PNG magic bytes 검증
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IllegalArgumentException("서명 이미지는 PNG 형식이어야 합니다.");
        }

        return fileStorageService.storeBytes(bytes, ".png", "signatures");
    }

    /** 관리자·작성자·참석자만 열람 */
    private boolean canView(CallerIdentity caller, MeetingMinutes minutes) {
        if (caller.type().name().equals(minutes.getAuthorType().name())
                && caller.refId().equals(minutes.getAuthorRefId())) {
            return true;
        }
        return minutes.getAttendees().stream()
                .anyMatch(attendee -> attendee.getRefId() != null
                        && attendee.getAttendeeType().name().equals(caller.type().name())
                        && attendee.getRefId().equals(caller.refId()));
    }

    private void requireCanView(CallerIdentity caller, MeetingMinutes minutes) {
        Long companyId = minutes.getCompany().getId();
        if (!companyId.equals(caller.companyId())) {
            throw new SecurityException("이 회의록을 열람할 권한이 없습니다");
        }
        if (accessService.isCompanyAdmin(caller, companyId) || canView(caller, minutes)) {
            return;
        }
        throw new SecurityException("이 회의록을 열람할 권한이 없습니다");
    }

    private void requireAuthorOrAdmin(CallerIdentity caller, MeetingMinutes minutes) {
        Long companyId = minutes.getCompany().getId();
        if (!companyId.equals(caller.companyId())) {
            throw new SecurityException("이 회의록을 수정할 권한이 없습니다");
        }
        boolean isAuthor = caller.type().name().equals(minutes.getAuthorType().name())
                && caller.refId().equals(minutes.getAuthorRefId());
        if (isAuthor || accessService.isCompanyAdmin(caller, companyId)) {
            return;
        }
        throw new SecurityException("작성자 또는 관리자만 할 수 있습니다");
    }

    private void requireNotCompleted(MeetingMinutes minutes) {
        if (minutes.getStatus() == MeetingMinutes.Status.COMPLETED) {
            throw new IllegalStateException("완료된 회의록은 수정할 수 없습니다");
        }
    }

    private MeetingMinutesAttendee requireAttendee(MeetingMinutes minutes, Long attendeeId) {
        return minutes.getAttendees().stream()
                .filter(attendee -> attendee.getId().equals(attendeeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("참석자를 찾을 수 없습니다: " + attendeeId));
    }

    private MeetingMinutes requireMinutes(Long id) {
        MeetingMinutes minutes = minutesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회의록을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(minutes.getCompany());
        return minutes;
    }

    private Company requireCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("회사를 찾을 수 없습니다: " + companyId));
        resourceScopeGuard.requireSameCompany(company);
        return company;
    }

    private CallerIdentity requireCaller(UserDetails userDetails) {
        CallerIdentity caller = accessService.resolveCaller(userDetails);
        if (caller == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }
        return caller;
    }

    private MeetingMinutesDTO toDetailDTO(MeetingMinutes minutes) {
        return MeetingMinutesDTO.detailOf(minutes, fileStorageService::getFileUrl);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
