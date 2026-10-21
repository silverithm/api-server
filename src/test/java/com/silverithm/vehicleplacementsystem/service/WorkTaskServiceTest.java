package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.PiiEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.WorkTaskDto;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.entity.WorkTask;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.WorkTaskRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/**
 * 업무진행도 — 2026-10-22 원장 요청("이름은 업무진행도", 담당자·기한·업무 내용·중요도·수행도,
 * 수행완료될 때까지 매일 알람). 담당자 유니크 키가 진짜로 걸리도록 H2에 저장까지 한다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class, PiiEncryptionConfig.class,
        EncryptedPiiConverter.class, WorkTaskPeople.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "pii.encryption.key=dGVzdC1vbmx5LXBpaS1rZXktZm9yLWpwYS1zbGljZS10ZXN0",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.hibernate.SQL=WARN"
})
class WorkTaskServiceTest {

    @Autowired private EntityManager em;
    @Autowired private WorkTaskRepository repository;
    @Autowired private WorkTaskPeople people;

    private final WorkTaskAccess access = mock(WorkTaskAccess.class);
    private final WorkTaskNotifier notifier = mock(WorkTaskNotifier.class);
    private WorkTaskService service;

    private Company 숲속;
    private Member 이수나;
    private Member 박소향;
    private Member 김보경;
    private AppUser 원장;
    private Member 다른기관직원;

    @BeforeEach
    void setUp() {
        숲속 = new Company("숲속재활어르신재가복지센터", null, null);
        em.persist(숲속);
        Company 다른기관 = new Company("다른 센터", null, null);
        em.persist(다른기관);
        이수나 = member(숲속, "이수나");
        박소향 = member(숲속, "박소향");
        김보경 = member(숲속, "김보경");
        다른기관직원 = member(다른기관, "남");
        원장 = new AppUser("김도형", "kdh@carev.kr", "x", UserRole.ROLE_ADMIN, null, 숲속, null);
        em.persist(원장);
        em.flush();
        service = new WorkTaskService(access, people, repository, notifier);
        로그인(이수나);
    }

    private Member member(Company company, String name) {
        String key = "u" + System.nanoTime();
        Member m = Member.builder().username(key).password("pw").name(name).email(key + "@t.kr")
                .role(Member.Role.OFFICE).status(Member.MemberStatus.ACTIVE).company(company).build();
        em.persist(m);
        return m;
    }

    private static String id(Member m) {
        return String.valueOf(m.getId());
    }

    private String id(AppUser admin) {
        return "admin_" + admin.getId();
    }

    private void 로그인(Member m) {
        when(access.requireCaller()).thenReturn(new WorkTaskAccess.Caller(id(m), m.getName(), 숲속, false));
    }

    private void 원장으로로그인() {
        when(access.requireCaller()).thenReturn(new WorkTaskAccess.Caller(id(원장), "김도형", 숲속, true));
    }

    private void 저장한다() {
        em.flush();
        em.clear();
    }

    private static WorkTaskDto.Input 업무(String content, String importance, Integer progress, LocalDate due,
                                         List<String> assignees) {
        return new WorkTaskDto.Input(content, importance, progress, due, assignees);
    }

    @SuppressWarnings("unchecked")
    private List<WorkTaskNotifier.Message> 보낸알림() {
        ArgumentCaptor<List<WorkTaskNotifier.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(notifier, atLeastOnce()).sendAfterCommit(captor.capture());
        List<WorkTaskNotifier.Message> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    @Test
    @DisplayName("담당자를 비우면 등록한 사람 자신 — 중요도는 보통, 자기에게 맡긴 건 알리지 않는다")
    void 내업무() {
        WorkTaskDto.View v = service.create(업무("블로그 작성", null, null, null, null));

        assertThat(v.importance()).isEqualTo("NORMAL");
        assertThat(v.progress()).isZero();
        assertThat(v.assignees()).extracting(WorkTaskDto.Person::name).containsExactly("이수나");
        assertThat(v.createdBy().name()).isEqualTo("이수나");
        assertThat(v.canEdit()).isTrue();
        assertThat(v.canDelete()).isTrue();
        assertThat(보낸알림()).isEmpty();
    }

    @Test
    @DisplayName("다른 사람에게 맡기면 그 사람에게 배정 알림 — 이름은 서버가 명단에서 찾는다")
    void 배정알림() {
        WorkTaskDto.View v = service.create(업무("사업계획서 작성", "HIGH", 0, LocalDate.of(2026, 10, 31),
                List.of(id(박소향), id(원장))));

        assertThat(v.assignees()).extracting(WorkTaskDto.Person::name).containsExactly("박소향", "김도형");
        List<WorkTaskNotifier.Message> sent = 보낸알림();
        assertThat(sent).extracting(WorkTaskNotifier.Message::personId).containsExactly(id(박소향), id(원장));
        assertThat(sent.get(0).title()).isEqualTo("새 업무가 배정되었습니다");
        assertThat(sent.get(0).body()).isEqualTo("이수나: 사업계획서 작성 · 기한 10/31");
        assertThat(sent.get(0).taskId()).isEqualTo(v.id());
    }

    @Test
    @DisplayName("다른 기관 사람이나 없는 사람은 담당자가 될 수 없다")
    void 다른기관담당자() {
        assertThatThrownBy(() -> service.create(업무("x", null, null, null, List.of(id(다른기관직원)))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.create(업무("x", null, null, null, List.of("99999"))))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("담당자를 바꿔 여러 번 저장해도 유니크 키에 안 걸리고, 새로 들어온 사람에게만 알린다")
    void 담당자바꾸기() {
        Long taskId = service.create(업무("간호일지 정리", null, 0, null, List.of(id(박소향), id(김보경)))).id();
        저장한다();

        service.update(taskId, 업무("간호일지 정리", null, 30, null, List.of(id(김보경), id(이수나), id(원장))));
        저장한다();
        service.update(taskId, 업무("간호일지 정리", null, 40, null, List.of(id(이수나), id(김보경), id(원장))));
        저장한다();

        WorkTask saved = repository.findById(taskId).orElseThrow();
        assertThat(saved.getAssignees()).extracting(a -> a.getPersonName())
                .containsExactlyInAnyOrder("김보경", "이수나", "김도형");
        // 두 번째 저장에서 원장만 새로 들어왔다(이수나는 본인이 저장했으니 알리지 않는다)
        assertThat(보낸알림()).extracting(WorkTaskNotifier.Message::personId)
                .containsExactly(id(박소향), id(김보경), id(원장));
    }

    @Test
    @DisplayName("수행도 100이면 완료 — 등록한 사람에게 알리고, 다시 낮추면 진행 중으로 돌아간다")
    void 완료() {
        Long taskId = service.create(업무("투약 기록 점검", null, 50, null, List.of(id(박소향)))).id();
        저장한다();

        로그인(박소향);
        WorkTaskDto.View done = service.updateProgress(taskId, 100);
        assertThat(done.completedAt()).isNotNull();
        assertThat(보낸알림()).filteredOn(m -> m.title().equals("업무를 마쳤습니다"))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.personId()).isEqualTo(id(이수나));
                    assertThat(m.body()).isEqualTo("박소향님이 '투약 기록 점검' 업무를 마쳤습니다");
                });
        저장한다();

        WorkTaskDto.View reopened = service.updateProgress(taskId, 90);
        assertThat(reopened.completedAt()).isNull();
        assertThat(reopened.progress()).isEqualTo(90);
    }

    @Test
    @DisplayName("고치기는 관리자·등록한 사람·담당자, 지우기는 관리자·등록한 사람만")
    void 권한() {
        Long taskId = service.create(업무("식단표 올리기", null, 0, null, List.of(id(박소향)))).id();
        저장한다();

        로그인(김보경);
        assertThat(service.list()).singleElement().satisfies(v -> {
            assertThat(v.canEdit()).isFalse();
            assertThat(v.canDelete()).isFalse();
        });
        assertThatThrownBy(() -> service.updateProgress(taskId, 10))
                .extracting(e -> ((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        로그인(박소향);
        assertThat(service.updateProgress(taskId, 20).progress()).isEqualTo(20);
        assertThatThrownBy(() -> service.delete(taskId))
                .extracting(e -> ((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        원장으로로그인();
        service.delete(taskId);
        저장한다();
        assertThat(repository.findById(taskId)).isEmpty();
    }

    @Test
    @DisplayName("목록: 진행 중은 중요 → 기한 이른 순, 끝낸 건 아래에, 일주일 넘게 지난 건 숨긴다")
    void 목록순서() {
        service.create(업무("보통·기한 없음", "NORMAL", 0, null, null));
        service.create(업무("낮음", "LOW", 0, LocalDate.now(), null));
        service.create(업무("보통·내일", "NORMAL", 0, LocalDate.now().plusDays(1), null));
        service.create(업무("중요", "HIGH", 0, LocalDate.now().plusDays(10), null));
        Long done = service.create(업무("끝낸 것", "HIGH", 100, null, null)).id();
        Long old = service.create(업무("오래전 끝낸 것", "HIGH", 100, null, null)).id();
        저장한다();
        em.createNativeQuery("UPDATE work_tasks SET completed_at = :at WHERE id = :id")
                .setParameter("at", LocalDateTime.now().minusDays(8)).setParameter("id", old).executeUpdate();

        assertThat(service.list()).extracting(WorkTaskDto.View::content)
                .containsExactly("중요", "보통·내일", "보통·기한 없음", "낮음", "끝낸 것");
        assertThat(service.list().get(4).id()).isEqualTo(done);
    }

    @Test
    @DisplayName("입력 검사 — 빈 내용, 0~100 밖 수행도, 모르는 중요도")
    void 입력검사() {
        assertThatThrownBy(() -> service.create(업무("  ", null, null, null, null)))
                .hasMessage("업무 내용을 적어 주세요");
        assertThatThrownBy(() -> service.create(업무("x", null, 101, null, null)))
                .hasMessage("수행도는 0~100 사이로 적어 주세요");
        assertThatThrownBy(() -> service.create(업무("x", "URGENT", null, null, null)))
                .hasMessage("중요도는 중요·보통·낮음 중에서 골라 주세요");
        assertThatThrownBy(() -> service.create(업무("x".repeat(301), null, null, null, null)))
                .hasMessage("업무 내용은 300자까지 적을 수 있습니다");
    }

    @Test
    @DisplayName("매일 알림 — 사람마다 한 통, 끝낸 업무는 빼고, 가장 급한 업무로 잇는다")
    void 매일알림() {
        service.create(업무("사업계획서", "HIGH", 30, LocalDate.of(2026, 10, 20), List.of(id(박소향), id(김보경))));
        service.create(업무("블로그", "NORMAL", 0, null, List.of(id(박소향))));
        service.create(업무("끝난 것", "HIGH", 100, null, List.of(id(박소향))));
        저장한다();

        WorkTaskReminderScheduler scheduler =
                new WorkTaskReminderScheduler(repository, notifier, mock(RedisUtils.class));
        List<WorkTaskNotifier.Message> messages = scheduler.buildMessages(LocalDate.of(2026, 10, 22));

        assertThat(messages).extracting(WorkTaskNotifier.Message::personName).containsExactlyInAnyOrder("박소향", "김보경");
        WorkTaskNotifier.Message 소향 = messages.stream().filter(m -> m.personName().equals("박소향")).findFirst().orElseThrow();
        assertThat(소향.title()).isEqualTo("진행 중인 업무 2건 · 기한 지남 1건");
        assertThat(소향.body()).isEqualTo("· 사업계획서 30% · 기한 지남\n· 블로그 0%");
    }

    @Test
    @DisplayName("매일 알림 문구 — 세 줄까지, 나머지는 '외 n건', 기한은 오늘까지·내일까지·D-n")
    void 알림문구() {
        LocalDate today = LocalDate.of(2026, 10, 22);
        assertThat(WorkTaskReminderText.dueLabel(today, today)).isEqualTo("오늘까지");
        assertThat(WorkTaskReminderText.dueLabel(today.plusDays(1), today)).isEqualTo("내일까지");
        assertThat(WorkTaskReminderText.dueLabel(today.plusDays(5), today)).isEqualTo("D-5");
        assertThat(WorkTaskReminderText.dueLabel(today.minusDays(1), today)).isEqualTo("기한 지남");
        assertThat(WorkTaskReminderText.dueLabel(null, today)).isNull();

        for (int i = 1; i <= 5; i++) {
            service.create(업무("업무 " + i, "NORMAL", i * 10, null, null));
        }
        저장한다();
        WorkTaskReminderText.Text text = WorkTaskReminderText.dailySummary(repository.findAllInProgress(), today);
        assertThat(text.title()).isEqualTo("진행 중인 업무 5건");
        assertThat(text.body()).isEqualTo("· 업무 1 10%\n· 업무 2 20%\n· 업무 3 30%\n외 2건");
    }
}
