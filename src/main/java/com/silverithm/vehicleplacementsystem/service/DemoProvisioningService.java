package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Notice;
import com.silverithm.vehicleplacementsystem.entity.Position;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleLabel;
import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatRoomRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.NoticeRepository;
import com.silverithm.vehicleplacementsystem.repository.PositionRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationLimitRepository;
import com.silverithm.vehicleplacementsystem.repository.VacationRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험하기(데모) 테넌트 생성 서비스.
 * 방문자마다 격리된 데모 Company + 관리자 계정 + 전 도메인 시드 데이터를 만들고
 * signin과 동일한 형태의 응답을 반환한다. 데모 테넌트는 {@link DemoCleanupScheduler}가
 * demo_expires_at 경과 후 통째로 삭제한다.
 *
 * 일반 signup과 달리 슬랙 알림·이메일·FreeSubscriptionHistory 기록을 남기지 않는다
 * (무료체험 이력 오염 방지). JWT subject는 반드시 email이어야 한다 — 인증 필터가
 * subject를 email로 조회하기 때문(signup()의 name-subject와 달리 signin 패턴을 따름).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoProvisioningService {

    public static final int DEMO_RETENTION_DAYS = 7;
    private static final String DEMO_EMAIL_DOMAIN = "@demo.carev.kr";
    private static final String DEMO_ADMIN_NAME = "체험 관리자";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PositionRepository positionRepository;
    private final MemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final ElderRepository elderRepository;
    private final VacationRequestRepository vacationRequestRepository;
    private final VacationLimitRepository vacationLimitRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleLabelRepository scheduleLabelRepository;
    private final ScheduleTaskRepository scheduleTaskRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final NoticeRepository noticeRepository;
    private final ApprovalTemplateRepository approvalTemplateRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CompanyCodeService companyCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SigninResponseDTO provisionDemoTenant() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email = "demo-" + suffix + DEMO_EMAIL_DOMAIN;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(DEMO_RETENTION_DAYS);

        Company company = new Company("행복요양 주간보호센터 (체험)", "서울특별시 중구 세종대로 110", null);
        company.updateCompanyCode(companyCodeService.generateUniqueCode());
        company.markAsDemo(expiresAt);
        // 가입용 공개 기관 목록(웹·앱)에 데모 기관이 노출되지 않게 숨긴다
        company.updateExpose(false);
        companyRepository.save(company);

        TokenInfo tokenInfo = jwtTokenProvider.generateToken(email, Collections.singleton(UserRole.ROLE_ADMIN));
        String customerKey = UUID.randomUUID().toString();
        AppUser admin = new AppUser(DEMO_ADMIN_NAME, email, passwordEncoder.encode(UUID.randomUUID().toString()),
                UserRole.ROLE_ADMIN, tokenInfo.getRefreshToken(), company, customerKey);
        userRepository.save(admin);

        Subscription subscription = seedSubscription(admin, now, expiresAt);
        List<Position> positions = seedPositions(company);
        List<Member> members = seedMembers(company, suffix, positions);
        seedEmployees(company);
        seedElderly(company);
        seedVacations(company, members);
        seedVacationLimits(company);
        seedSchedules(company, admin, members);
        seedNotices(company, admin);
        seedApprovals(company, admin, members);
        seedChat(company, admin, members);

        log.info("[Demo] 데모 테넌트 생성 완료: companyId={}, email={}, expiresAt={}",
                company.getId(), email, expiresAt);

        return new SigninResponseDTO(admin.getId(), admin.getUsername(), company.getId(), company.getName(),
                company.getCompanyAddress(), company.getAddressName(), company.getCompanyCode(),
                tokenInfo, new SubscriptionResponseDTO(subscription), customerKey);
    }

    private Subscription seedSubscription(AppUser admin, LocalDateTime now, LocalDateTime expiresAt) {
        Subscription subscription = Subscription.builder()
                .planName(SubscriptionType.ENTERPRISE)
                .billingType(SubscriptionBillingType.FREE)
                .startDate(now)
                .endDate(expiresAt)
                .status(SubscriptionStatus.ACTIVE)
                .amount(0)
                .user(admin)
                .build();
        return subscriptionRepository.save(subscription);
    }

    private List<Position> seedPositions(Company company) {
        List<Position> positions = List.of(
                position(company, "원장", "센터 운영 총괄", Member.Role.ADMIN, 0),
                position(company, "사회복지사", "상담 및 프로그램 운영", Member.Role.OFFICE, 1),
                position(company, "요양보호사", "어르신 돌봄", Member.Role.CAREGIVER, 2),
                position(company, "간호조무사", "건강 관리 및 투약 보조", Member.Role.OFFICE, 3)
        );
        return positionRepository.saveAll(positions);
    }

    private Position position(Company company, String name, String description, Member.Role role, int sortOrder) {
        return Position.builder()
                .company(company)
                .name(name)
                .description(description)
                .memberRole(role)
                .sortOrder(sortOrder)
                .build();
    }

    private List<Member> seedMembers(Company company, String suffix, List<Position> positions) {
        Position welfare = positions.get(1);
        Position caregiver = positions.get(2);
        Position nurseAide = positions.get(3);

        List<Member> members = List.of(
                member(company, suffix, 1, "김영희", Member.Role.CAREGIVER, caregiver, "요양팀"),
                member(company, suffix, 2, "박순자", Member.Role.CAREGIVER, caregiver, "요양팀"),
                member(company, suffix, 3, "이민수", Member.Role.CAREGIVER, caregiver, "요양팀"),
                member(company, suffix, 4, "최지은", Member.Role.OFFICE, welfare, "사무팀"),
                member(company, suffix, 5, "정다혜", Member.Role.OFFICE, nurseAide, "사무팀")
        );
        return memberRepository.saveAll(members);
    }

    private Member member(Company company, String suffix, int index, String name, Member.Role role,
                          Position position, String department) {
        return Member.builder()
                .username("demo-" + suffix + "-member" + index)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(name)
                .email("demo-" + suffix + "-member" + index + DEMO_EMAIL_DOMAIN)
                .phoneNumber("010-0000-000" + index)
                .role(role)
                .status(Member.MemberStatus.ACTIVE)
                .department(department)
                .position(position.getName())
                .positionEntity(position)
                .company(company)
                .build();
    }

    private void seedEmployees(Company company) {
        employeeRepository.save(new Employee("서울 중구 을지로 45", "김영희", company, null, 4, false, null));
        employeeRepository.save(new Employee("서울 중구 퇴계로 88", "이민수", company, null, 4, true, null));
        employeeRepository.save(new Employee("서울 성동구 왕십리로 12", "박순자", company, null, 4, false, null));
    }

    private void seedElderly(Company company) {
        String[][] elders = {
                {"김말순", "서울 중구 다산로 33", "true"},
                {"박봉례", "서울 중구 청구로 17", "false"},
                {"이순남", "서울 성동구 금호로 21", "false"},
                {"최점례", "서울 중구 신당동 432", "true"},
                {"강복순", "서울 중구 약수동 15", "false"},
                {"윤정례", "서울 성동구 행당로 5", "false"},
                {"한갑수", "서울 중구 장충단로 72", "false"},
                {"서옥자", "서울 중구 동호로 240", "true"},
        };
        for (String[] elder : elders) {
            elderRepository.save(new Elderly(elder[0], elder[1], null, Boolean.parseBoolean(elder[2]), company));
        }
    }

    private void seedVacations(Company company, List<Member> members) {
        LocalDate today = LocalDate.now();
        Member kim = members.get(0);
        Member park = members.get(1);
        Member lee = members.get(2);
        Member choi = members.get(3);
        Member jung = members.get(4);

        vacationRequestRepository.saveAll(List.of(
                vacation(company, kim, today.minusDays(7), VacationRequest.VacationStatus.APPROVED,
                        "regular", "FULL_DAY", "가족 행사 참석"),
                vacation(company, park, today.minusDays(3), VacationRequest.VacationStatus.APPROVED,
                        "regular", "HALF_DAY_AM", "병원 진료"),
                vacation(company, lee, today.plusDays(2), VacationRequest.VacationStatus.APPROVED,
                        "regular", "FULL_DAY", "개인 사유"),
                vacation(company, choi, today.plusDays(4), VacationRequest.VacationStatus.APPROVED,
                        "regular", "FULL_DAY", "연차 사용"),
                vacation(company, jung, today.plusDays(5), VacationRequest.VacationStatus.APPROVED,
                        "substitute", "FULL_DAY", "지난주 토요일 근무 대체휴무"),
                vacation(company, kim, today.plusDays(3), VacationRequest.VacationStatus.PENDING,
                        "regular", "FULL_DAY", "자녀 학교 행사"),
                vacation(company, park, today.plusDays(8), VacationRequest.VacationStatus.PENDING,
                        "regular", "HALF_DAY_PM", "관공서 방문"),
                vacation(company, lee, today.plusDays(11), VacationRequest.VacationStatus.PENDING,
                        "regular", "FULL_DAY", "개인 사유")
        ));
    }

    private VacationRequest vacation(Company company, Member member, LocalDate date,
                                     VacationRequest.VacationStatus status, String type, String duration,
                                     String reason) {
        return VacationRequest.builder()
                .userName(member.getName())
                .date(date)
                .status(status)
                .role(member.getRole() == Member.Role.CAREGIVER ? "caregiver" : "office")
                .reason(reason)
                .userId(String.valueOf(member.getId()))
                .type(type)
                .duration(duration)
                .company(company)
                .build();
    }

    private void seedVacationLimits(Company company) {
        LocalDate today = LocalDate.now();
        for (int day = 0; day <= 14; day++) {
            LocalDate date = today.plusDays(day);
            vacationLimitRepository.save(VacationLimit.builder()
                    .date(date).maxPeople(2).role("caregiver").company(company).build());
            vacationLimitRepository.save(VacationLimit.builder()
                    .date(date).maxPeople(1).role("office").company(company).build());
        }
    }

    private void seedSchedules(Company company, AppUser admin, List<Member> members) {
        LocalDate today = LocalDate.now();
        String adminId = String.valueOf(admin.getId());

        ScheduleLabel program = scheduleLabelRepository.save(
                ScheduleLabel.builder().company(company).name("프로그램").color("#3B82F6").build());
        ScheduleLabel meeting = scheduleLabelRepository.save(
                ScheduleLabel.builder().company(company).name("회의").color("#F59E0B").build());
        ScheduleLabel event = scheduleLabelRepository.save(
                ScheduleLabel.builder().company(company).name("행사").color("#10B981").build());

        Schedule pastMeeting = scheduleRepository.save(Schedule.builder()
                .company(company).title("월례 직원회의").content("8월 운영 계획 및 안전 점검 공유")
                .category(ScheduleCategory.MEETING).label(meeting)
                .startDate(today.minusDays(4)).startTime(LocalTime.of(14, 0))
                .endDate(today.minusDays(4)).endTime(LocalTime.of(15, 0))
                .authorId(adminId).authorName(admin.getUsername())
                .build());
        pastMeeting.updateCompletion(true, adminId, admin.getUsername());

        scheduleRepository.save(Schedule.builder()
                .company(company).title("치매예방 인지활동 프로그램").content("색칠하기·회상놀이 진행")
                .category(ScheduleCategory.EVENT).label(program)
                .startDate(today.minusDays(2)).startTime(LocalTime.of(10, 0))
                .endDate(today.minusDays(2)).endTime(LocalTime.of(11, 30))
                .authorId(adminId).authorName(admin.getUsername())
                .build());

        scheduleRepository.save(Schedule.builder()
                .company(company).title("보호자 상담 주간").content("어르신별 보호자 전화·방문 상담")
                .category(ScheduleCategory.OTHER)
                .startDate(today).endDate(today.plusDays(4)).isAllDay(true)
                .authorId(adminId).authorName(admin.getUsername())
                .build());

        Member kim = members.get(0);
        Member lee = members.get(2);
        Schedule safety = scheduleRepository.save(Schedule.builder()
                .company(company).title("소방안전 교육").content("전 직원 대상 소방 대피 훈련 및 소화기 사용법 교육")
                .category(ScheduleCategory.TRAINING).label(event)
                .startDate(today.plusDays(3)).startTime(LocalTime.of(15, 0))
                .endDate(today.plusDays(3)).endTime(LocalTime.of(16, 0))
                .managerMemberId(lee.getId()).managerName(lee.getName())
                .authorId(adminId).authorName(admin.getUsername())
                .build());
        scheduleTaskRepository.save(ScheduleTask.builder()
                .schedule(safety).content("소화기 위치 점검 및 교체 대상 확인")
                .assigneeMemberId(lee.getId()).assigneeName(lee.getName())
                .createdById(adminId).createdByName(admin.getUsername()).sortOrder(0)
                .build());
        scheduleTaskRepository.save(ScheduleTask.builder()
                .schedule(safety).content("대피로 안내문 부착")
                .assigneeMemberId(kim.getId()).assigneeName(kim.getName())
                .createdById(adminId).createdByName(admin.getUsername()).sortOrder(1)
                .build());
        scheduleParticipantRepository.save(ScheduleParticipant.builder()
                .schedule(safety).memberId(kim.getId()).memberName(kim.getName())
                .status(ScheduleParticipant.ParticipantStatus.ACCEPTED)
                .build());
        scheduleParticipantRepository.save(ScheduleParticipant.builder()
                .schedule(safety).memberId(lee.getId()).memberName(lee.getName())
                .status(ScheduleParticipant.ParticipantStatus.ACCEPTED)
                .build());

        scheduleRepository.save(Schedule.builder()
                .company(company).title("가을 나들이 사전답사").content("근교 공원 답사 및 차량 동선 확인")
                .category(ScheduleCategory.EVENT).label(event)
                .startDate(today.plusDays(12)).isAllDay(true)
                .endDate(today.plusDays(12))
                .authorId(adminId).authorName(admin.getUsername())
                .build());
    }

    private void seedNotices(Company company, AppUser admin) {
        String adminId = String.valueOf(admin.getId());
        LocalDateTime now = LocalDateTime.now();
        int month = LocalDate.now().getMonthValue();

        Notice welcome = Notice.builder()
                .title("케어브이 체험 모드에 오신 것을 환영합니다")
                .content("이 센터는 체험용 예시 데이터로 구성되어 있습니다.\n\n"
                        + "직원 관리, 휴무 승인, 월간 일정, 전자결재, 채팅 등 실제 케어브이의 모든 기능을 자유롭게 눌러보세요. "
                        + "여기서 만들거나 수정한 데이터는 다른 방문자에게 보이지 않으며, 7일 후 자동으로 삭제됩니다.\n\n"
                        + "직원 앱 연동과 푸시 알림은 체험판에서 동작하지 않습니다. 정식 가입 후 이용하실 수 있습니다.")
                .priority(Notice.NoticePriority.HIGH)
                .status(Notice.NoticeStatus.PUBLISHED)
                .isPinned(true)
                .authorId(adminId).authorName(admin.getUsername())
                .company(company)
                .publishedAt(now)
                .build();
        noticeRepository.save(welcome);

        noticeRepository.save(Notice.builder()
                .title(month + "월 프로그램 일정 안내")
                .content("이번 달 인지활동 프로그램과 건강 체조 일정을 안내드립니다.\n\n"
                        + "- 매주 화·목 오전 10시: 치매예방 인지활동\n- 매주 수 오후 2시: 건강 체조\n"
                        + "- 셋째 주 금요일: 생신잔치\n\n자세한 내용은 월간일정 탭에서 확인해주세요.")
                .priority(Notice.NoticePriority.NORMAL)
                .status(Notice.NoticeStatus.PUBLISHED)
                .authorId(adminId).authorName(admin.getUsername())
                .company(company)
                .publishedAt(now.minusDays(2))
                .build());

        noticeRepository.save(Notice.builder()
                .title("무더위 대비 어르신 건강관리 안내")
                .content("폭염이 이어지고 있습니다. 어르신 수분 섭취를 수시로 확인해주시고, "
                        + "실외 활동은 오전 시간대로 조정해주세요. 이상 증상 발견 시 간호조무사에게 즉시 알려주시기 바랍니다.")
                .priority(Notice.NoticePriority.NORMAL)
                .status(Notice.NoticeStatus.PUBLISHED)
                .authorId(adminId).authorName(admin.getUsername())
                .company(company)
                .publishedAt(now.minusDays(4))
                .build());

        noticeRepository.save(Notice.builder()
                .title("차량 운행시간 변경 안내")
                .content("다음 주부터 오전 송영 차량 출발 시간이 8시 30분에서 8시 40분으로 변경됩니다. "
                        + "담당 어르신 보호자분들께 미리 안내 부탁드립니다.")
                .priority(Notice.NoticePriority.NORMAL)
                .status(Notice.NoticeStatus.PUBLISHED)
                .authorId(adminId).authorName(admin.getUsername())
                .company(company)
                .publishedAt(now.minusDays(6))
                .build());
    }

    private void seedApprovals(Company company, AppUser admin, List<Member> members) {
        LocalDate today = LocalDate.now();
        String adminProcessorId = "admin_" + admin.getId();

        ApprovalTemplate leaveTemplate = approvalTemplateRepository.save(ApprovalTemplate.builder()
                .company(company)
                .name("휴가 신청서")
                .description("연차, 반차, 병가 등 휴가 신청")
                .templateType("form")
                .formSchema("""
                        {"version":1,"fields":[
                        {"id":"leave-type","type":"select","label":"휴가 유형","required":true,"width":"half","options":[
                        {"label":"연차","value":"annual"},{"label":"반차(오전)","value":"half-am"},
                        {"label":"반차(오후)","value":"half-pm"},{"label":"병가","value":"sick"},{"label":"기타","value":"other"}]},
                        {"id":"start-date","type":"date","label":"시작일","required":true,"width":"half"},
                        {"id":"end-date","type":"date","label":"종료일","required":true,"width":"half"},
                        {"id":"reason","type":"textarea","label":"사유","required":true,"placeholder":"휴가 사유를 입력하세요"}]}
                        """)
                .isActive(true)
                .build());

        ApprovalTemplate suppliesTemplate = approvalTemplateRepository.save(ApprovalTemplate.builder()
                .company(company)
                .name("비품 구매 신청서")
                .description("소모품·비품 구매 요청")
                .templateType("form")
                .formSchema("""
                        {"version":1,"fields":[
                        {"id":"item-name","type":"text","label":"품목명","required":true,"width":"half"},
                        {"id":"quantity","type":"number","label":"수량","required":true,"width":"half","validation":{"min":1}},
                        {"id":"unit-price","type":"number","label":"단가(원)","required":false,"width":"half","validation":{"min":0}},
                        {"id":"purchase-reason","type":"textarea","label":"구매 사유","required":true}]}
                        """)
                .isActive(true)
                .build());

        Member kim = members.get(0);
        Member park = members.get(1);
        Member choi = members.get(3);

        ApprovalRequest approvedLeave = ApprovalRequest.builder()
                .company(company).template(leaveTemplate)
                .title("연차 사용 신청")
                .requesterId(String.valueOf(kim.getId())).requesterName(kim.getName())
                .status(ApprovalRequest.ApprovalStatus.APPROVED)
                .formData(String.format(
                        "{\"leave-type\":\"annual\",\"start-date\":\"%s\",\"end-date\":\"%s\",\"reason\":\"가족 행사 참석으로 연차를 신청합니다.\"}",
                        today.minusDays(7), today.minusDays(7)))
                .processedBy(adminProcessorId).processedByName(admin.getUsername())
                .processedAt(LocalDateTime.now().minusDays(8))
                .build();
        approvalRequestRepository.save(approvedLeave);

        ApprovalRequest approvedHalfDay = ApprovalRequest.builder()
                .company(company).template(leaveTemplate)
                .title("오전 반차 신청")
                .requesterId(String.valueOf(park.getId())).requesterName(park.getName())
                .status(ApprovalRequest.ApprovalStatus.APPROVED)
                .formData(String.format(
                        "{\"leave-type\":\"half-am\",\"start-date\":\"%s\",\"end-date\":\"%s\",\"reason\":\"병원 진료로 오전 반차를 신청합니다.\"}",
                        today.minusDays(3), today.minusDays(3)))
                .processedBy(adminProcessorId).processedByName(admin.getUsername())
                .processedAt(LocalDateTime.now().minusDays(4))
                .build();
        approvalRequestRepository.save(approvedHalfDay);

        ApprovalRequest pendingSupplies = ApprovalRequest.builder()
                .company(company).template(suppliesTemplate)
                .title("어르신 위생용품 구매 신청")
                .requesterId(String.valueOf(choi.getId())).requesterName(choi.getName())
                .status(ApprovalRequest.ApprovalStatus.PENDING)
                .formData("{\"item-name\":\"성인용 위생 물티슈 (대형)\",\"quantity\":20,\"unit-price\":4500,"
                        + "\"purchase-reason\":\"재고가 이번 주 내 소진될 예정이라 보충 구매가 필요합니다.\"}")
                .build();
        approvalRequestRepository.save(pendingSupplies);
    }

    private void seedChat(Company company, AppUser admin, List<Member> members) {
        String adminId = String.valueOf(admin.getId());
        // 채팅 참가자 식별자는 관리자(AppUser id)와 직원(Member id)이 모두 원시 숫자 문자열이라
        // id가 우연히 겹치면 (chat_room_id, user_id) 유니크 제약에 걸린다 — 겹치는 직원은 제외한다.
        List<Member> chatMembers = members.stream()
                .filter(m -> !String.valueOf(m.getId()).equals(adminId))
                .toList();

        ChatRoom noticeRoom = chatRoomRepository.save(ChatRoom.builder()
                .name("전체 공지방")
                .description("센터 전체 공지 및 전달사항")
                .company(company)
                .createdBy(adminId).createdByName(admin.getUsername())
                .build());
        chatParticipantRepository.save(participant(noticeRoom, adminId, admin.getUsername(),
                ChatParticipant.ParticipantRole.ADMIN));
        for (Member member : chatMembers) {
            chatParticipantRepository.save(participant(noticeRoom, String.valueOf(member.getId()), member.getName(),
                    ChatParticipant.ParticipantRole.MEMBER));
        }
        chatMessageRepository.save(message(noticeRoom, adminId, admin.getUsername(), "원장",
                "안녕하세요, 전체 공지방입니다. 전달사항은 이 방에서 안내드릴게요."));
        chatMessageRepository.save(message(noticeRoom, adminId, admin.getUsername(), "원장",
                "내일 오전 송영 차량이 10분 늦게 출발합니다. 담당 선생님들은 보호자분들께 안내 부탁드려요."));
        Member officeReplier = chatMembers.stream()
                .filter(m -> m.getRole() == Member.Role.OFFICE).findFirst().orElse(null);
        if (officeReplier != null) {
            chatMessageRepository.save(message(noticeRoom, String.valueOf(officeReplier.getId()),
                    officeReplier.getName(), officeReplier.getPosition(),
                    "네, 확인했습니다. 보호자분들께 문자로 안내드리겠습니다."));
        }
        noticeRoom.updateLastMessageAt();

        ChatRoom careRoom = chatRoomRepository.save(ChatRoom.builder()
                .name("요양보호사 소통방")
                .description("요양팀 업무 공유")
                .company(company)
                .createdBy(adminId).createdByName(admin.getUsername())
                .build());
        chatParticipantRepository.save(participant(careRoom, adminId, admin.getUsername(),
                ChatParticipant.ParticipantRole.ADMIN));
        List<Member> caregivers = chatMembers.stream()
                .filter(m -> m.getRole() == Member.Role.CAREGIVER).toList();
        for (Member member : caregivers) {
            chatParticipantRepository.save(participant(careRoom, String.valueOf(member.getId()), member.getName(),
                    ChatParticipant.ParticipantRole.MEMBER));
        }
        if (!caregivers.isEmpty()) {
            Member first = caregivers.get(0);
            chatMessageRepository.save(message(careRoom, String.valueOf(first.getId()), first.getName(),
                    first.getPosition(), "김말순 어르신 오늘 컨디션이 좋으셔서 인지활동 끝까지 참여하셨어요."));
            Member last = caregivers.get(caregivers.size() - 1);
            if (last != first) {
                chatMessageRepository.save(message(careRoom, String.valueOf(last.getId()), last.getName(),
                        last.getPosition(), "한갑수 어르신은 점심 식사량이 평소보다 적으셨습니다. 내일 참고해주세요."));
            }
        }
        chatMessageRepository.save(message(careRoom, adminId, admin.getUsername(), "원장",
                "공유 감사합니다. 특이사항은 간호조무사 선생님께도 전달해주세요."));
        careRoom.updateLastMessageAt();
    }

    private ChatParticipant participant(ChatRoom room, String userId, String userName,
                                        ChatParticipant.ParticipantRole role) {
        return ChatParticipant.builder()
                .chatRoom(room)
                .userId(userId)
                .userName(userName)
                .role(role)
                .build();
    }

    private ChatMessage message(ChatRoom room, String senderId, String senderName, String senderPosition,
                                String content) {
        return ChatMessage.builder()
                .chatRoom(room)
                .senderId(senderId)
                .senderName(senderName)
                .senderPosition(senderPosition)
                .type(ChatMessage.MessageType.TEXT)
                .content(content)
                .build();
    }
}
