package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleCategorySettingRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일정 수행완료(updateCompletion) 권한 판정 회귀 테스트.
 *
 * 운영에서 실제로 있었던 상황: app_user.id=3(회사 A 시설장)을 담당자로 지정한 일정을,
 * members.id=3인 다른 회사 직원이 로그인해서 수행완료 버튼을 눌러도 통과해버리는 결함이 있었다.
 * managerMemberId만 비교하고 managerType(어느 테이블 id인지)을 안 봤기 때문이다.
 * ScheduleServiceApplyManagerTest가 "저장" 경로를 덮는다면, 이 테스트는 "권한 판정" 경로를 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("일정 수행완료 권한 판정(managerType) 회귀 테스트")
class ScheduleServiceUpdateCompletionManagerTypeTest {

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
    private Company company;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(
                scheduleRepository, scheduleCategorySettingRepository, scheduleLabelRepository,
                scheduleParticipantRepository, scheduleTaskRepository, companyRepository,
                transactionManager, memberRepository, userRepository, fcmService, resourceScopeGuard);

        company = mock(Company.class);
        // 예외를 던지는 케이스는 save/categorySettingsFor까지 가지 않으므로 lenient로 둔다.
        lenient().when(scheduleCategorySettingRepository.findByCompanyId(any())).thenReturn(List.of());
        lenient().when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Schedule scheduleWithManager(Schedule.ManagerType managerType, Long managerMemberId) {
        return Schedule.builder()
                .id(10L)
                .company(company)
                .title("시설장 담당 일정")
                .category(ScheduleCategory.MEETING)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 1))
                .isAllDay(true)
                .authorId("author@carev.kr")
                .authorName("김작성")
                .managerMemberId(managerMemberId)
                .managerType(managerType)
                .build();
    }

    @Test
    @DisplayName("회귀 방지: 담당자가 ADMIN이면, id가 우연히 같은 members.id를 가진 직원은 수행완료할 수 없다")
    void memberCannotCompleteWhenIdCoincidentallyMatchesAdminManager() {
        // 운영 사고 재현: app_user.id=3(회사 A 시설장)을 담당자로 지정, 로그인한 사람은 members.id=3
        Schedule schedule = scheduleWithManager(Schedule.ManagerType.ADMIN, 3L);
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        assertThrows(IllegalStateException.class,
                () -> scheduleService.updateCompletion(10L, true, "coincidence@carev.kr", "우연직원", 3L, false));
    }

    @Test
    @DisplayName("담당자가 MEMBER면 같은 id의 직원 본인은 정상적으로 수행완료할 수 있다")
    void memberCanCompleteWhenManagerTypeIsMember() {
        Schedule schedule = scheduleWithManager(Schedule.ManagerType.MEMBER, 3L);
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        var result = scheduleService.updateCompletion(10L, true, "manager@carev.kr", "박담당", 3L, false);

        assertTrue(result.getIsCompleted());
    }

    @Test
    @DisplayName("담당자가 ADMIN이어도 관리자 계정(isAdmin=true)은 대행 처리할 수 있다")
    void adminCanCompleteEvenWhenManagerTypeIsAdmin() {
        Schedule schedule = scheduleWithManager(Schedule.ManagerType.ADMIN, 3L);
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        // 관리자 계정은 resolveMemberId가 null을 주므로 memberId=null로 호출된다
        var result = scheduleService.updateCompletion(10L, true, "admin@carev.kr", "이관리", null, true);

        assertTrue(result.getIsCompleted());
    }

    @Test
    @DisplayName("담당자가 ADMIN이면 일반 직원은(id가 안 겹쳐도) 대행할 수 없다")
    void memberCannotCompleteAdminManagedScheduleEvenWithoutIdCollision() {
        Schedule schedule = scheduleWithManager(Schedule.ManagerType.ADMIN, 3L);
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        assertThrows(IllegalStateException.class,
                () -> scheduleService.updateCompletion(10L, true, "other@carev.kr", "박직원", 99L, false));
    }
}
