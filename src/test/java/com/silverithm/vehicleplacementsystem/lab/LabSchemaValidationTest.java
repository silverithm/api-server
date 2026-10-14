package com.silverithm.vehicleplacementsystem.lab;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.PiiEncryptionConfig;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBathLog;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBudget;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBusinessPlan;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCommitteeMeeting;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCommitteeMember;
import com.silverithm.vehicleplacementsystem.lab.entity.LabElderDocument;
import com.silverithm.vehicleplacementsystem.lab.entity.LabIntakeInquiry;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicalVisit;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffingMonth;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramFeedback;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramPlan;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramSchedule;
import com.silverithm.vehicleplacementsystem.lab.entity.LabSatisfactionResponse;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBudgetLine;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareDelivery;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareEvent;
import com.silverithm.vehicleplacementsystem.lab.entity.LabClaimReview;
import com.silverithm.vehicleplacementsystem.lab.entity.LabDischargeReferral;
import com.silverithm.vehicleplacementsystem.lab.entity.LabElderContract;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicationDose;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicationRequest;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicineCheck;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicineStock;
import com.silverithm.vehicleplacementsystem.lab.entity.LabGuardianNotice;
import com.silverithm.vehicleplacementsystem.lab.entity.LabRehabSession;
import com.silverithm.vehicleplacementsystem.lab.entity.LabResourceLink;
import com.silverithm.vehicleplacementsystem.lab.entity.LabVolunteerLog;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareRecord;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCopayBill;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCarePlan;
import com.silverithm.vehicleplacementsystem.lab.entity.LabConsultation;
import com.silverithm.vehicleplacementsystem.lab.entity.LabEvalCheck;
import com.silverithm.vehicleplacementsystem.lab.entity.LabDonation;
import com.silverithm.vehicleplacementsystem.lab.entity.LabLedgerEntry;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMealPlan;
import com.silverithm.vehicleplacementsystem.lab.entity.LabPayrollLine;
import com.silverithm.vehicleplacementsystem.lab.entity.LabPayrollProfile;
import com.silverithm.vehicleplacementsystem.lab.entity.LabSafetyCheck;
import com.silverithm.vehicleplacementsystem.lab.entity.LabNursingLog;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgram;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffTraining;
import com.silverithm.vehicleplacementsystem.lab.entity.LabRiskAssessment;
import java.util.Map;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.orm.hibernate5.SpringBeanContainer;

/**
 * 실험실 엔티티가 실제 MySQL의 V1.96.0~V1.103.0 표와 맞는지 Hibernate SchemaValidator로 확인한다.
 *
 * <p>운영은 ddl-auto=validate라 엔티티와 표가 한 칸만 어긋나도 기동이 막힌다. H2 테스트(create-drop)는
 * 엔티티에서 표를 만들기 때문에 이 어긋남을 절대 잡지 못한다. 그래서 마이그레이션이 적용된 MySQL을
 * 가리키는 환경변수가 있을 때만 돌고, 없으면 건너뛴다.
 *
 * <ul>
 *   <li>LAB_SCHEMA_DB_URL — 예: jdbc:mysql://127.0.0.1:3307/scratch (필수. 없으면 건너뜀)</li>
 *   <li>LAB_SCHEMA_DB_USER — 기본 root</li>
 *   <li>LAB_SCHEMA_DB_PASSWORD — 기본 빈 문자열</li>
 * </ul>
 */
class LabSchemaValidationTest {

    // 실험실 엔티티(1차~8차)가 MySQL의 V1.96.0~V1.103.0 표와 일치한다
    @Test
    void entitiesMatchMigratedMysqlSchema() {
        String url = System.getenv("LAB_SCHEMA_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "LAB_SCHEMA_DB_URL이 없어 MySQL 스키마 검증을 건너뜁니다");
        String user = System.getenv().getOrDefault("LAB_SCHEMA_DB_USER", "root");
        String password = System.getenv().getOrDefault("LAB_SCHEMA_DB_PASSWORD", "");

        // 컨버터는 Spring 빈이라(암호화 키 필요) Hibernate가 직접 만들 수 없다 — 최소한의 컨텍스트를 세워 건넨다.
        // 검증은 스키마만 보므로 키 값은 아무 값이면 된다.
        try (AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext()) {
            spring.getEnvironment().getPropertySources().addFirst(new MapPropertySource("labSchemaTest", Map.of(
                    "billing.encryption.key", "dGVzdC1vbmx5LWtleS1mb3ItbGFiLXNjaGVtYS10ZXN0",
                    "pii.encryption.key", "dGVzdC1vbmx5LXBpaS1rZXktZm9yLWxhYi1zY2hlbWEtdGVzdA==")));
            spring.register(BillingKeyEncryptionConfig.class, PiiEncryptionConfig.class,
                    EncryptedPiiConverter.class);
            spring.refresh();

            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting(AvailableSettings.URL, url)
                    .applySetting(AvailableSettings.USER, user)
                    .applySetting(AvailableSettings.PASS, password)
                    .applySetting(AvailableSettings.DRIVER, "com.mysql.cj.jdbc.Driver")
                    .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect")
                    // 앱과 같은 이름 규칙 — Spring Boot 기본값
                    .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, CamelCaseToUnderscoresNamingStrategy.class.getName())
                    .applySetting(AvailableSettings.IMPLICIT_NAMING_STRATEGY, SpringImplicitNamingStrategy.class.getName())
                    // Hibernate 6에서 SchemaValidator는 독립 클래스가 아니라 hbm2ddl.auto=validate로 도는
                    // 스키마 검증 도구(SchemaManagementToolCoordinator)다 — 운영과 같은 경로를 그대로 탄다
                    .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
                    .applySetting(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(spring.getBeanFactory()))
                    .build();
            try {
                MetadataSources sources = new MetadataSources(registry)
                        .addAnnotatedClass(LabCareRecord.class)
                        .addAnnotatedClass(LabCareDelivery.class)
                        .addAnnotatedClass(LabCopayBill.class)
                        .addAnnotatedClass(LabRiskAssessment.class)
                        .addAnnotatedClass(LabEvalCheck.class)
                        // 2차(V1.97.0)
                        .addAnnotatedClass(LabNursingLog.class)
                        .addAnnotatedClass(LabProgram.class)
                        .addAnnotatedClass(LabStaffTraining.class)
                        .addAnnotatedClass(LabConsultation.class)
                        .addAnnotatedClass(LabCarePlan.class)
                        // 3차(V1.98.0)
                        .addAnnotatedClass(LabMealPlan.class)
                        .addAnnotatedClass(LabSafetyCheck.class)
                        // 4차(V1.99.0)
                        .addAnnotatedClass(LabPayrollProfile.class)
                        .addAnnotatedClass(LabPayrollLine.class)
                        .addAnnotatedClass(LabDonation.class)
                        .addAnnotatedClass(LabLedgerEntry.class)
                        // 5차(V1.100.0)
                        .addAnnotatedClass(LabCareEvent.class)
                        .addAnnotatedClass(LabRehabSession.class)
                        .addAnnotatedClass(LabGuardianNotice.class)
                        .addAnnotatedClass(LabVolunteerLog.class)
                        .addAnnotatedClass(LabResourceLink.class)
                        .addAnnotatedClass(LabBudget.class)
                        .addAnnotatedClass(LabBudgetLine.class)
                        // 6차(V1.101.0)
                        .addAnnotatedClass(LabMedicationRequest.class)
                        .addAnnotatedClass(LabMedicationDose.class)
                        .addAnnotatedClass(LabMedicineStock.class)
                        .addAnnotatedClass(LabMedicineCheck.class)
                        .addAnnotatedClass(LabBathLog.class)
                        .addAnnotatedClass(LabElderContract.class)
                        .addAnnotatedClass(LabDischargeReferral.class)
                        .addAnnotatedClass(LabClaimReview.class)
                        // 7차(V1.102.0)
                        .addAnnotatedClass(LabProgramPlan.class)
                        .addAnnotatedClass(LabProgramSchedule.class)
                        .addAnnotatedClass(LabProgramFeedback.class)
                        .addAnnotatedClass(LabBusinessPlan.class)
                        .addAnnotatedClass(LabSatisfactionResponse.class)
                        .addAnnotatedClass(LabIntakeInquiry.class)
                        // 8차(V1.103.0)
                        .addAnnotatedClass(LabStaffingMonth.class)
                        .addAnnotatedClass(LabElderDocument.class)
                        .addAnnotatedClass(LabMedicalVisit.class)
                        .addAnnotatedClass(LabCommitteeMember.class)
                        .addAnnotatedClass(LabCommitteeMeeting.class);
                Metadata metadata = sources.buildMetadata();
                // 세션 팩토리를 만드는 순간 표와 엔티티를 대조한다. 어긋난 컬럼·타입이 있으면
                // SchemaManagementException으로 실패한다
                try (SessionFactory ignored = metadata.buildSessionFactory()) {
                    // 검증은 생성 시점에 끝난다
                }
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
    }
}
