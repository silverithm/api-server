package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String addressName;

    @Column(name = "company_code", nullable = false, unique = true, length = 32)
    private String companyCode;

    @Column(name = "expose", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean expose = true;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "company_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "company_longitude"))
    })
    private Location companyAddress;

    @Column(name = "seal_url", length = 1000)
    private String sealUrl;

    /** 기관 대표 홈페이지 주소 (선택). 공문 발신부에 찍히는 값이기도 하다. */
    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    /**
     * 기관이 함께 운영하는 주소들 (블로그·밴드 등) — [{"name":"블로그","url":"..."}] 형태의 JSON.
     * 사이드바 바로가기는 이 목록을 쓰고, 첫 항목이 대표(homepageUrl)가 된다.
     */
    @Column(name = "homepage_links", columnDefinition = "JSON")
    private String homepageLinks;

    // ── 공문 발신부(문서 하단 주소·연락처 줄)에 찍히는 값 ──
    // 주소는 addressName, 홈페이지는 homepageUrl을 그대로 쓴다.

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    /** 팩스 — 공문에서는 "전송"으로 표기한다 */
    @Column(name = "fax_number", length = 30)
    private String faxNumber;

    /** 공문 담당자 E-MAIL */
    @Column(name = "contact_email")
    private String contactEmail;

    /** 공개 구분 (공개/부분공개/비공개). 미설정이면 화면에서 "공개"로 본다. */
    @Column(name = "disclosure_type", length = 20)
    private String disclosureType;

    @Column(name = "is_demo", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isDemo = false;

    @Column(name = "demo_expires_at")
    private LocalDateTime demoExpiresAt;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<AppUser> users;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<Employee> employees;

    public Company(String name, String addressName, Location companyAddress) {
        this.name = name;
        this.addressName = addressName;
        this.companyAddress = companyAddress;
        this.companyCode = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    public static Company of(String companyName, String addressName, Location companyLocation) {
        return new Company(companyName, addressName, companyLocation);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateAddress(String addressName, Location companyAddress) {
        this.addressName = addressName;
        this.companyAddress = companyAddress;
    }

    public void updateCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    /** 여러 주소 목록(JSON). 빈 값이면 해제. */
    public void updateHomepageLinks(String homepageLinks) {
        this.homepageLinks = (homepageLinks == null || homepageLinks.isBlank()) ? null : homepageLinks;
    }

    /** null 또는 빈 값이면 등록 해제로 본다. */
    public void updateHomepageUrl(String homepageUrl) {
        this.homepageUrl = (homepageUrl == null || homepageUrl.isBlank()) ? null : homepageUrl.trim();
    }

    public void updateExpose(boolean expose) {
        this.expose = expose;
    }

    /** 공문 발신부에 찍히는 기관 정보 (우편번호·전화·팩스·담당자 메일·공개구분) */
    public void updateDocumentFooter(String postalCode, String phoneNumber, String faxNumber,
                                     String contactEmail, String disclosureType) {
        this.postalCode = blankToNull(postalCode);
        this.phoneNumber = blankToNull(phoneNumber);
        this.faxNumber = blankToNull(faxNumber);
        this.contactEmail = blankToNull(contactEmail);
        this.disclosureType = blankToNull(disclosureType);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public void updateSeal(String sealUrl) {
        this.sealUrl = sealUrl;
    }

    public void markAsDemo(LocalDateTime expiresAt) {
        this.isDemo = true;
        this.demoExpiresAt = expiresAt;
    }

    public boolean isDemoCompany() {
        return Boolean.TRUE.equals(this.isDemo);
    }
}
