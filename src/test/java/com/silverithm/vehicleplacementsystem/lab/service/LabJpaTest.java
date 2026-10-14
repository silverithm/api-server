package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.PiiEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 실험실 서비스 테스트가 공유하는 H2 JPA 슬라이스 설정.
 *
 * <p>운영 프로파일을 올리는 @SpringBootTest 대신 슬라이스를 쓰고, 컨버터가 요구하는 암호화 키 빈과
 * 테스트 전용 키를 함께 준다. 서비스와 가짜 접근 판정을 전부 한 번에 올려 컨텍스트를 하나만 쓴다. 키는 컨버터 빈을 만들기 위한 값일 뿐 실제 데이터를 다루지 않는다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class, PiiEncryptionConfig.class,
        EncryptedPiiConverter.class, LabElderDirectory.class, LabTestAccess.class,
        LabCareRecordService.class, LabCareDeliveryService.class, LabCopayBillService.class,
        LabRiskAssessmentService.class, LabEvalCheckService.class,
        LabNursingLogService.class, LabProgramService.class, LabStaffTrainingService.class,
        LabConsultationService.class, LabCarePlanService.class, LabStaffService.class,
        LabMealPlanService.class, LabSafetyCheckService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "pii.encryption.key=dGVzdC1vbmx5LXBpaS1rZXktZm9yLWpwYS1zbGljZS10ZXN0",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=WARN"
})
public @interface LabJpaTest {
}
