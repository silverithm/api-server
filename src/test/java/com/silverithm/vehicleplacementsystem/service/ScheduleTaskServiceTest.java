package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("일정 할 일(담당자 업무) 서비스 테스트")
class ScheduleTaskServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleLabelRepository scheduleLabelRepository;
    @Mock private ScheduleParticipantRepository scheduleParticipantRepository;
    @Mock private ScheduleTaskRepository scheduleTaskRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private FCMService fcmService;
    @Mock private ResourceScopeGuard resourceScopeGuard;

    @InjectMocks private ScheduleService scheduleService;

    private Schedule schedule;

    private static final Long ASSIGNEE_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 99L;

    @BeforeEach
    void setUp() {
        Company company = mock(Company.class);
        lenient().when(company.getId()).thenReturn(1L);

        schedule = Schedule.builder()
                .id(10L)
                .company(company)
                .title("8월 월례회의")
                .category(ScheduleCategory.MEETING)
                .startDate(LocalDate.of(2026, 8, 5))
                .endDate(LocalDate.of(2026, 8, 5))
                .isAllDay(true)
                .sendNotification(false)
                .isCompleted(false)
                .authorId("author@carev.kr")
                .authorName("김작성")
                .build();
    }

    private ScheduleTask task(Long id, Long assigneeId, boolean completed) {
        return ScheduleTask.builder()
                .id(id)
                .schedule(schedule)
                .content("소방점검표 작성")
                .assigneeMemberId(assigneeId)
                .assigneeName(assigneeId == null ? null : "김요양")
                .isCompleted(completed)
                .createdById("author@carev.kr")
                .createdByName("김작성")
                .sortOrder(0)
                .build();
    }

    @Test
    @DisplayName("담당자를 지정해 할 일을 추가할 수 있다")
    void createTaskWithAssignee() {
        Member member = mock(Member.class);
        when(member.getName()).thenReturn("김요양");
        when(memberRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(member));
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
        // 첫 호출은 정렬 순서 계산, 두 번째는 저장 후 완료 상태 재계산
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(0L, 1L);
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(0L);

        ScheduleTaskDTO result = scheduleService.createTask(10L,
                ScheduleTaskRequestDTO.builder().content("소방점검표 작성").assigneeMemberId(ASSIGNEE_ID).build(),
                "author@carev.kr", "김작성");

        assertEquals("소방점검표 작성", result.getContent());
        assertEquals(ASSIGNEE_ID, result.getAssigneeMemberId());
        assertEquals("김요양", result.getAssigneeName());
        assertFalse(result.getIsCompleted());
    }

    @Test
    @DisplayName("내용이 비어 있으면 할 일을 추가할 수 없다")
    void createTaskRejectsBlankContent() {
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createTask(10L,
                ScheduleTaskRequestDTO.builder().content("  ").build(), "author@carev.kr", "김작성"));
        verify(scheduleTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("담당자 본인은 자기 할 일을 완료 처리할 수 있다")
    void assigneeCanCompleteOwnTask() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(1L);
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(1L);

        ScheduleTaskDTO result = scheduleService.updateTaskCompletion(
                100L, true, "care@carev.kr", "김요양", ASSIGNEE_ID, false);

        assertTrue(result.getIsCompleted());
        assertEquals("김요양", result.getCompletedByName());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("담당자가 아닌 직원은 남의 할 일을 완료 처리할 수 없다")
    void otherMemberCannotCompleteTask() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));

        assertThrows(IllegalStateException.class, () -> scheduleService.updateTaskCompletion(
                100L, true, "other@carev.kr", "박직원", OTHER_MEMBER_ID, false));

        assertFalse(t.getIsCompleted());
        verify(scheduleTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자는 남의 할 일도 대신 완료 처리할 수 있다")
    void adminCanCompleteAnyTask() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(1L);
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(1L);

        ScheduleTaskDTO result = scheduleService.updateTaskCompletion(
                100L, true, "admin@carev.kr", "이관리", null, true);

        assertTrue(result.getIsCompleted());
    }

    @Test
    @DisplayName("담당자 미지정 할 일은 구성원 누구나 완료 처리할 수 있다")
    void unassignedTaskCompletableByAnyone() {
        ScheduleTask t = task(100L, null, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(1L);
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(1L);

        ScheduleTaskDTO result = scheduleService.updateTaskCompletion(
                100L, true, "other@carev.kr", "박직원", OTHER_MEMBER_ID, false);

        assertTrue(result.getIsCompleted());
    }

    @Test
    @DisplayName("할 일이 전부 끝나면 일정이 자동으로 완료된다")
    void scheduleAutoCompletesWhenAllTasksDone() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(3L);
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(3L);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(i -> i.getArgument(0));

        scheduleService.updateTaskCompletion(100L, true, "care@carev.kr", "김요양", ASSIGNEE_ID, false);

        assertTrue(schedule.getIsCompleted());
        verify(scheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("일부만 끝난 상태에서는 일정이 완료되지 않는다")
    void scheduleStaysIncompleteWhenSomeTasksRemain() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(3L);
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(2L);

        scheduleService.updateTaskCompletion(100L, true, "care@carev.kr", "김요양", ASSIGNEE_ID, false);

        assertFalse(schedule.getIsCompleted());
        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("완료된 일정에서 할 일을 하나 해제하면 일정도 다시 미완료가 된다")
    void scheduleReopensWhenTaskUnchecked() {
        schedule.updateCompletion(true, null, "할 일 전체 완료");
        ScheduleTask t = task(100L, ASSIGNEE_ID, true);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));
        when(scheduleTaskRepository.countByScheduleId(10L)).thenReturn(3L);
        when(scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(10L)).thenReturn(2L);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(i -> i.getArgument(0));

        scheduleService.updateTaskCompletion(100L, false, "care@carev.kr", "김요양", ASSIGNEE_ID, false);

        assertFalse(schedule.getIsCompleted());
        assertNull(schedule.getCompletedAt());
    }

    @Test
    @DisplayName("담당자 본인은 자기 할 일의 내용을 고칠 수 있다")
    void assigneeCanEditOwnTask() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));
        when(scheduleTaskRepository.save(any(ScheduleTask.class))).thenAnswer(i -> i.getArgument(0));

        ScheduleTaskDTO result = scheduleService.updateTask(100L,
                ScheduleTaskRequestDTO.builder().content("소방점검표 작성 및 제출").assigneeMemberId(ASSIGNEE_ID).build(),
                "care@carev.kr", ASSIGNEE_ID, false);

        assertEquals("소방점검표 작성 및 제출", result.getContent());
    }

    @Test
    @DisplayName("무관한 직원은 할 일을 삭제할 수 없다")
    void unrelatedMemberCannotDeleteTask() {
        ScheduleTask t = task(100L, ASSIGNEE_ID, false);
        when(scheduleTaskRepository.findById(100L)).thenReturn(Optional.of(t));

        assertThrows(IllegalStateException.class,
                () -> scheduleService.deleteTask(100L, "other@carev.kr", OTHER_MEMBER_ID, false));

        verify(scheduleTaskRepository, never()).delete(any());
    }
}
