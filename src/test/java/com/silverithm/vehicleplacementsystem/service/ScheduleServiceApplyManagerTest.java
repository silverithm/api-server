package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleCategorySettingRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일정 담당자 지정 로직(applyManager) 테스트.
 *
 * 운영에서 관리자(app_user) id를 담당자로 저장했을 때 members 테이블만 조회해
 * 엉뚱한 회사의 직원이 담당자로 기록되던 사고(V1.88 이전)의 회귀 테스트다.
 * private 메서드라 리플렉션으로 직접 호출한다 — createSchedule 전체를 띄우면
 * 이 로직과 무관한 참석자/알림 처리까지 목킹해야 해서 테스트가 무거워진다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("일정 담당자 지정(applyManager) 테스트")
class ScheduleServiceApplyManagerTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleCategorySettingRepository scheduleCategorySettingRepository;
    @Mock
    private ScheduleLabelRepository scheduleLabelRepository;
    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;
    @Mock
    private ScheduleTaskRepository scheduleTaskRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FCMService fcmService;
    @Mock
    private ResourceScopeGuard resourceScopeGuard;

    private ScheduleService scheduleService;
    private Method applyManager;

    private Company companyA;
    private Company companyB;

    @BeforeEach
    void setUp() throws Exception {
        scheduleService = new ScheduleService(
                scheduleRepository, scheduleCategorySettingRepository, scheduleLabelRepository,
                scheduleParticipantRepository, scheduleTaskRepository, companyRepository,
                transactionManager, memberRepository, userRepository, fcmService, resourceScopeGuard);

        applyManager = ScheduleService.class.getDeclaredMethod(
                "applyManager", Schedule.class, Long.class, String.class);
        applyManager.setAccessible(true);

        companyA = mock(Company.class);
        lenient().when(companyA.getId()).thenReturn(1L);
        companyB = mock(Company.class);
        lenient().when(companyB.getId()).thenReturn(2L);
    }

    private Schedule scheduleFor(Company company) {
        return Schedule.builder().id(100L).company(company).title("테스트 일정").build();
    }

    private void invoke(Schedule schedule, Long managerId, String managerType) throws Exception {
        applyManager.invoke(scheduleService, schedule, managerId, managerType);
    }

    @Test
    @DisplayName("managerType=MEMBER면 같은 회사 직원을 담당자로 저장한다")
    void assignsMemberManagerInSameCompany() throws Exception {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(9L);
        when(member.getName()).thenReturn("박직원");
        when(member.getCompany()).thenReturn(companyA);
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 9L, "MEMBER");

        assertEquals(9L, schedule.getManagerMemberId());
        assertEquals("박직원", schedule.getManagerName());
        assertEquals(Schedule.ManagerType.MEMBER, schedule.getManagerType());
    }

    @Test
    @DisplayName("managerType=ADMIN이면 members가 아니라 app_user에서 조회해 담당자로 저장한다")
    void assignsAdminManagerInSameCompany() throws Exception {
        AppUser admin = mock(AppUser.class);
        when(admin.getId()).thenReturn(3L);
        when(admin.getUsername()).thenReturn("김도형");
        when(admin.getCompany()).thenReturn(companyA);
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 3L, "ADMIN");

        assertEquals(3L, schedule.getManagerMemberId());
        assertEquals("김도형", schedule.getManagerName());
        assertEquals(Schedule.ManagerType.ADMIN, schedule.getManagerType());
    }

    @Test
    @DisplayName("managerType이 비어 있으면(구버전 클라이언트) MEMBER로 조회한다")
    void blankManagerTypeDefaultsToMember() throws Exception {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(9L);
        when(member.getName()).thenReturn("박직원");
        when(member.getCompany()).thenReturn(companyA);
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 9L, null);

        assertEquals(9L, schedule.getManagerMemberId());
        assertEquals(Schedule.ManagerType.MEMBER, schedule.getManagerType());
    }

    @Test
    @DisplayName("회귀 방지: id가 우연히 겹쳐도(app_user=3, 다른 회사 member=3) MEMBER로 잘못 저장하지 않는다")
    void doesNotMisreadAdminIdAsMemberFromDifferentCompany() throws Exception {
        // 운영에서 실제로 있었던 상황: app_user.id=3(회사 A 시설장), members.id=3(회사 B의 요양보호사)
        // managerType=ADMIN이므로 memberRepository는 아예 조회되면 안 된다 — 그래서 스텁하지 않는다
        // (스텁했다가 호출 안 되면 오히려 "혹시 MEMBER 분기를 탔는지" 검증이 느슨해진다).

        AppUser admin = mock(AppUser.class);
        when(admin.getId()).thenReturn(3L);
        when(admin.getUsername()).thenReturn("김도형");
        when(admin.getCompany()).thenReturn(companyA);
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 3L, "ADMIN");

        assertEquals(3L, schedule.getManagerMemberId());
        assertEquals(Schedule.ManagerType.ADMIN, schedule.getManagerType());
        assertEquals("김도형", schedule.getManagerName());
        org.mockito.Mockito.verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("다른 회사 직원 id를 담당자로 넣으면 저장하지 않는다(IDOR 방지)")
    void ignoresMemberFromDifferentCompany() throws Exception {
        Member otherCompanyMember = mock(Member.class);
        when(otherCompanyMember.getCompany()).thenReturn(companyB);
        when(memberRepository.findById(5L)).thenReturn(Optional.of(otherCompanyMember));

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 5L, "MEMBER");

        assertNull(schedule.getManagerMemberId());
        assertNull(schedule.getManagerName());
    }

    @Test
    @DisplayName("다른 회사 관리자 id를 담당자로 넣으면 저장하지 않는다(IDOR 방지)")
    void ignoresAdminFromDifferentCompany() throws Exception {
        AppUser otherCompanyAdmin = mock(AppUser.class);
        when(otherCompanyAdmin.getCompany()).thenReturn(companyB);
        when(userRepository.findById(4L)).thenReturn(Optional.of(otherCompanyAdmin));

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 4L, "ADMIN");

        assertNull(schedule.getManagerMemberId());
        assertNull(schedule.getManagerName());
    }

    @Test
    @DisplayName("존재하지 않는 id면 저장하지 않는다")
    void ignoresMissingManagerId() throws Exception {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        Schedule schedule = scheduleFor(companyA);
        invoke(schedule, 999L, "MEMBER");

        assertNull(schedule.getManagerMemberId());
        assertNull(schedule.getManagerName());
    }

    @Test
    @DisplayName("managerId가 null이면 담당자를 해제한다")
    void clearsManagerWhenIdIsNull() throws Exception {
        Schedule schedule = scheduleFor(companyA);
        schedule.setManagerMemberId(9L);
        schedule.setManagerName("박직원");
        schedule.setManagerType(Schedule.ManagerType.MEMBER);

        invoke(schedule, null, null);

        assertNull(schedule.getManagerMemberId());
        assertNull(schedule.getManagerName());
        assertEquals(Schedule.ManagerType.MEMBER, schedule.getManagerType());
    }
}
