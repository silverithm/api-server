package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.VoiceMessage;
import com.silverithm.vehicleplacementsystem.lab.dto.LabGrievanceDto;
import jakarta.persistence.EntityManager;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabGrievanceSummaryServiceTest {

    @Autowired private LabGrievanceSummaryService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Company other;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        other = LabTestFixtures.company(em, "다른기관");
        access.companyId = forest.getId();
    }

    private VoiceMessage voice(Company company, VoiceMessage.VoiceType type, VoiceMessage.VoiceStatus status,
                               boolean anonymous, LocalDateTime created, LocalDateTime repliedAt) {
        VoiceMessage message = VoiceMessage.builder().company(company).type(type).title("비밀 제목 ").content("비밀 본문")
                .isAnonymous(anonymous).authorType(ApprovalStep.ApproverType.MEMBER).authorRefId(1L)
                .authorName("김작성").status(status).adminReply(repliedAt == null ? null : "비밀 답변")
                .repliedAt(repliedAt).build();
        em.persist(message);
        em.flush();
        // createdAt은 저장 시각으로 채워지므로 직접 고쳐 쓴다
        jdbc.update("UPDATE voice_messages SET created_at = ? WHERE id = ?", created, message.getId());
        em.clear();
        return message;
    }

    // 우리 기관의 '고충'만 센다 — 다른 기관의 고충, 건의(SUGGESTION), 다른 해의 고충은 세지 않는다
    @Test
    void countsOnlyOwnCompanyGrievances() {
        LocalDateTime now = LabValidation.seoulNow();
        int year = now.getYear();
        LocalDateTime jan = LocalDateTime.of(year, 1, 10, 9, 0);
        voice(forest, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.RECEIVED, true, jan, null);
        voice(forest, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.IN_REVIEW, false,
                jan.plusDays(1), null);
        voice(forest, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.RESOLVED, false, jan.plusDays(2),
                jan.plusDays(3));
        voice(forest, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.ON_HOLD, true, jan.plusDays(3),
                jan.plusDays(7));
        // 세지 않을 것들
        voice(other, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.RECEIVED, true, jan, null);
        voice(forest, VoiceMessage.VoiceType.SUGGESTION, VoiceMessage.VoiceStatus.RECEIVED, false, jan, null);
        voice(forest, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.RECEIVED, false,
                LocalDateTime.of(year - 1, 12, 31, 23, 59), null);

        LabGrievanceDto.Summary summary = service.summary(String.valueOf(year));
        assertThat(summary.year()).isEqualTo(year);
        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.anonymous()).isEqualTo(2);
        assertThat(summary.received()).isEqualTo(1);
        assertThat(summary.inReview()).isEqualTo(1);
        assertThat(summary.resolved()).isEqualTo(1);
        assertThat(summary.onHold()).isEqualTo(1);
        assertThat(summary.replied()).isEqualTo(2);
        // 답한 건 둘: 1일·4일 -> 중앙값 2.5일
        assertThat(summary.medianReplyDays()).isEqualTo(2.5);
        // 조치완료가 아닌 건 중 가장 오래된 것은 1월 10일 접수
        assertThat(summary.oldestOpenDays()).isEqualTo(
                (int) java.time.temporal.ChronoUnit.DAYS.between(jan, now));

        // 다른 기관에서 보면 그 기관 것 하나뿐
        access.companyId = other.getId();
        LabGrievanceDto.Summary theirs = service.summary(String.valueOf(year));
        assertThat(theirs.total()).isEqualTo(1);
        assertThat(theirs.replied()).isZero();
        assertThat(theirs.medianReplyDays()).isNull();
        // 지난해
        access.companyId = forest.getId();
        LabGrievanceDto.Summary last = service.summary(String.valueOf(year - 1));
        assertThat(last.total()).isEqualTo(1);
        assertThat(service.summary("2019").total()).isZero();
        assertThat(service.summary("2019").oldestOpenDays()).isNull();
        assertRejected(() -> service.summary("20x6"), HttpStatus.BAD_REQUEST, "연도");
        assertRejected(() -> service.summary(null), HttpStatus.BAD_REQUEST, "연도");
    }

    // 응답에는 제목·본문·작성자·답변 글을 담을 칸이 없고, 값에도 새어 나오지 않는다
    @Test
    void noContentExposed() throws Exception {
        List<String> forbidden = List.of("title", "content", "authorName", "authorRefId", "adminReply", "reply",
                "author", "body", "message");
        List<String> names = Arrays.stream(LabGrievanceDto.Summary.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(names).containsExactly("year", "total", "anonymous", "received", "inReview", "resolved", "onHold",
                "replied", "medianReplyDays", "oldestOpenDays");
        assertThat(names).doesNotContainAnyElementsOf(forbidden);
        // 문자열 칸 자체가 없다 — 숫자 칸뿐이다
        for (RecordComponent c : LabGrievanceDto.Summary.class.getRecordComponents()) {
            assertThat(CharSequence.class.isAssignableFrom(c.getType())).as(c.getName()).isFalse();
        }

        voice(forest, VoiceMessage.VoiceType.GRIEVANCE, VoiceMessage.VoiceStatus.RESOLVED, false,
                LocalDateTime.of(LabValidation.seoulToday().getYear(), 1, 2, 9, 0),
                LocalDateTime.of(LabValidation.seoulToday().getYear(), 1, 4, 9, 0));
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(service.summary(String.valueOf(LabValidation.seoulToday().getYear())));
        assertThat(json).doesNotContain("비밀").doesNotContain("김작성");
    }
}
