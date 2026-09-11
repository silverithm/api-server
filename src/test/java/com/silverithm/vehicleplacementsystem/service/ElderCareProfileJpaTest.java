package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.PiiEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileRequest;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.CareGrade;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.MealType;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 케어 정보가 '암호문으로 저장되고 평문으로 읽히는지', 그리고 '어르신을 지우면 같이 사라지는지'를 못박는다.
 *
 * 주민번호는 유출 시 되돌릴 수 없는 값이라, 컨버터가 어느 날 조용히 빠져 평문이 그대로 들어가는 사고가
 * 가장 무섭다. 그래서 엔티티가 아니라 DB에 실제로 들어간 문자열을 네이티브 쿼리로 직접 본다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class, PiiEncryptionConfig.class,
        EncryptedPiiConverter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "pii.encryption.key=dGVzdC1vbmx5LXBpaS1rZXktZm9yLWpwYS1zbGljZS10ZXN0",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:eldercare;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=WARN"
})
class ElderCareProfileJpaTest {

    @Autowired private ElderRepository elderRepository;
    @Autowired private EntityManager em;

    private static final String RESIDENT_NUMBER = "4102032830514";

    @Test
    @DisplayName("주민번호는 암호문으로 저장되고, 읽을 때만 평문으로 돌아온다")
    void residentNumberIsStoredEncrypted() {
        Long elderId = saveElderWithProfile();

        String stored = (String) em.createNativeQuery(
                        "SELECT resident_number FROM elder_care_profile WHERE elderly_id = " + elderId)
                .getSingleResult();

        assertThat(stored).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        assertThat(stored).doesNotContain(RESIDENT_NUMBER);

        Elderly reloaded = elderRepository.findById(elderId).orElseThrow();
        assertThat(reloaded.getCareProfile().getResidentNumber()).isEqualTo(RESIDENT_NUMBER);
        assertThat(reloaded.getCareProfile().getCareGrade()).isEqualTo(CareGrade.GRADE_3);
        assertThat(reloaded.getCareProfile().getMealNote()).isEqualTo("생선 못 드심");
    }

    @Test
    @DisplayName("응답에는 마스킹된 값과 만 나이만 실린다")
    void responseCarriesOnlyMaskedNumber() {
        Long elderId = saveElderWithProfile();

        ElderCareProfileDTO dto = ElderCareProfileDTO.from(
                elderRepository.findById(elderId).orElseThrow().getCareProfile());

        assertThat(dto.residentNumberMasked()).isEqualTo("410203-2******");
        assertThat(dto.age()).isNotNull();
        // 간식·저녁은 보내지 않아도 기본이 켜져 있다
        assertThat(dto.morningSnack()).isTrue();
        assertThat(dto.dinner()).isTrue();
    }

    @Test
    @DisplayName("어르신을 지우면 케어 정보도 함께 사라진다 — 주인 없는 주민번호를 남기지 않는다")
    void deletingElderRemovesProfile() {
        Long elderId = saveElderWithProfile();

        elderRepository.delete(elderRepository.findById(elderId).orElseThrow());
        em.flush();
        em.clear();

        Number remaining = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM elder_care_profile WHERE elderly_id = " + elderId)
                .getSingleResult();

        assertThat(remaining.intValue()).isZero();
        assertThat(elderRepository.findById(elderId)).isEmpty();
    }

    private Long saveElderWithProfile() {
        Elderly elderly = new Elderly("김순자", true, null);
        ElderCareProfile profile = elderly.careProfileOrCreate();
        profile.apply(request(), true);

        Elderly saved = elderRepository.save(elderly);
        em.flush();
        em.clear();
        return saved.getId();
    }

    private ElderCareProfileRequest request() {
        return new ElderCareProfileRequest(
                RESIDENT_NUMBER,
                ElderCareProfileSupport.deriveBirthDate(RESIDENT_NUMBER),
                ElderCareProfileSupport.deriveGender(RESIDENT_NUMBER),
                CareGrade.GRADE_3,
                true, "화장실 이동 시 부축 필요",
                false, null,
                null, false,
                null, null,
                MealType.CHOPPED,
                null, null, null,
                "생선 못 드심",
                "9:40-50", "1,3째주는 방문목욕",
                true, false, true,
                "10시", null, "6시",
                "혈압약",
                "1호차", 2, "TV 앞 좌측",
                "말수가 적으심");
    }
}
