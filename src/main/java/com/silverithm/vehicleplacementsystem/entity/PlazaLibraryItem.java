package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 케어브이 광장 자료실 항목 — 파일은 S3(FileStorageService)에 저장, 여기는 메타데이터.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "plaza_library_items")
public class PlazaLibraryItem extends BaseEntity {

    public enum Category {
        FORM("form"), EVAL("eval"), PROGRAM("program"), ETC("etc");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static Category fromKey(String key) {
            for (Category c : values()) {
                if (c.key.equalsIgnoreCase(key)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("Unknown library category: " + key);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 300)
    private String fileName;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false)
    private String uploaderId;

    @Column(nullable = false, length = 100)
    private String uploaderName;

    @Column(length = 100)
    private String companyName;

    @Column(nullable = false)
    private int downloadCount;

    @Column(nullable = false)
    private boolean isHidden;
}
