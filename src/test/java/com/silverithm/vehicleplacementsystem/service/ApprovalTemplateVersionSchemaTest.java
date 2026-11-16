package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateVersion;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;

/**
 * 양식 버전 엔티티가 실제 MySQL의 V1.110.0 표와 맞는지 — 운영과 같은 ddl-auto=validate 경로로 확인한다.
 *
 * <p>2026-11-16 첫 배포 때 {@code kind}(VARCHAR(20))를 Hibernate가 MySQL ENUM으로 기대해 새 서버가 뜨지 못했다
 * (무중단 배포라 기존 서버가 계속 서빙). H2 테스트는 엔티티에서 표를 만들어 이 어긋남을 못 잡는다.
 * 마이그레이션을 적용한 MySQL을 가리키는 SCHEMA_DB_URL이 있을 때만 돈다(예: jdbc:mysql://127.0.0.1:3399/t).
 */
class ApprovalTemplateVersionSchemaTest {

    @Test
    void entityMatchesMigratedMysqlTable() {
        String url = System.getenv("SCHEMA_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "SCHEMA_DB_URL이 없어 MySQL 스키마 검증을 건너뜁니다");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.URL, url)
                .applySetting(AvailableSettings.USER, System.getenv().getOrDefault("SCHEMA_DB_USER", "root"))
                .applySetting(AvailableSettings.PASS, System.getenv().getOrDefault("SCHEMA_DB_PASSWORD", ""))
                .applySetting(AvailableSettings.DRIVER, "com.mysql.cj.jdbc.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect")
                .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, CamelCaseToUnderscoresNamingStrategy.class.getName())
                .applySetting(AvailableSettings.IMPLICIT_NAMING_STRATEGY, SpringImplicitNamingStrategy.class.getName())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(ApprovalTemplateVersion.class)
                    .buildMetadata();
            // 세션 팩토리를 만드는 순간 표와 엔티티를 대조한다 — 어긋나면 SchemaManagementException
            try (SessionFactory ignored = metadata.buildSessionFactory()) {
                // 검증은 생성 시점에 끝난다
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
