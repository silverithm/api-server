package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ScheduleDTO;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
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
@DisplayName("일정 수행완료(진행도) 서비스 테스트")
class ScheduleCompletionServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleLabelRepository scheduleLabelRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FCMService fcmService;

    @Mock
    private ResourceScopeGuard resourceScopeGuard;

    @InjectMocks
    private ScheduleService scheduleService;

    private Schedule schedule;

    @BeforeEach
    void setUp() {
        Company company = mock(Company.class);
        lenient().when(company.getId()).thenReturn(1L);

        schedule = Schedule.builder()
                .id(10L)
                .company(company)
                .title("월간 회의")
                .category(ScheduleCategory.MEETING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 1))
                .isAllDay(true)
                .sendNotification(false)
                .isCompleted(false)
                .authorId("author@carev.kr")
                .authorName("김작성")
                .build();
    }

    @Test
    @DisplayName("작성자 본인은 수행완료로 변경할 수 있고 처리자/시각이 기록된다")
    void authorCanCompleteSchedule() {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleDTO result = scheduleService.updateCompletion(10L, true, "author@carev.kr", "김작성", false);

        assertTrue(result.getIsCompleted());
        assertEquals("김작성", result.getCompletedByName());
        assertEquals("author@carev.kr", result.getCompletedById());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("관리자는 남의 일정도 수행완료로 변경할 수 있다")
    void adminCanCompleteOthersSchedule() {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleDTO result = scheduleService.updateCompletion(10L, true, "admin@carev.kr", "이관리", true);

        assertTrue(result.getIsCompleted());
        assertEquals("이관리", result.getCompletedByName());
    }

    @Test
    @DisplayName("작성자도 관리자도 아니면 수행완료로 변경할 수 없다")
    void otherUserCannotCompleteSchedule() {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        assertThrows(IllegalStateException.class,
                () -> scheduleService.updateCompletion(10L, true, "other@carev.kr", "박직원", false));

        assertFalse(schedule.getIsCompleted());
        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("완료 해제 시 처리자/시각 정보가 초기화된다")
    void uncompleteClearsCompletionInfo() {
        schedule.updateCompletion(true, "author@carev.kr", "김작성");
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleDTO result = scheduleService.updateCompletion(10L, false, "author@carev.kr", "김작성", false);

        assertFalse(result.getIsCompleted());
        assertNull(result.getCompletedAt());
        assertNull(result.getCompletedById());
        assertNull(result.getCompletedByName());
    }

    @Test
    @DisplayName("응답 JSON에 프론트엔드가 기대하는 isCompleted 키로 직렬화된다")
    void dtoSerializesCompletionFields() throws Exception {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleDTO dto = scheduleService.updateCompletion(10L, true, "author@carev.kr", "김작성", false);

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(dto);

        assertTrue(json.contains("\"isCompleted\":true"), json);
        assertTrue(json.contains("\"completedByName\":\"김작성\""), json);
        assertTrue(json.contains("\"isAllDay\":true"), json);
    }

    @Test
    @DisplayName("존재하지 않는 일정이면 예외가 발생한다")
    void missingScheduleThrows() {
        when(scheduleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> scheduleService.updateCompletion(99L, true, "author@carev.kr", "김작성", true));
    }
}
