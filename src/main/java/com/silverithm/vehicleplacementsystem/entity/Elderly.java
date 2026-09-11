package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@NoArgsConstructor
public class Elderly extends Node {


    /** 암호화 저장(AES-GCM). 컬럼 길이는 암호문 기준 — 평문 50자(UTF-8 150B)가 base64로 약 243자 */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(length = 512)
    private String name;

    /** 암호화 저장. 평문 200자(600B) → 암호문 약 841자 */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(length = 1024)
    private String homeAddressName;
    @Embedded
    private Location homeAddress;
    private boolean requiredFrontSeat;


    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /**
     * 케어 정보(1:1). 어르신을 지우면 함께 지운다 — 주민번호가 담긴 행이 주인 없이 남으면 안 된다.
     * (DB에도 ON DELETE CASCADE가 걸려 있어, JPA를 거치지 않는 삭제에서도 같은 결과가 된다)
     */
    @OneToOne(mappedBy = "elderly", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ElderCareProfile careProfile;

    public Elderly(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat, AppUser user) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
        this.user = user;
    }

    public Elderly(String name, boolean requiredFrontSeat, Company company) {
        this.name = name;
        this.requiredFrontSeat = requiredFrontSeat;
        this.company = company;
    }

    public Elderly(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat, Company company) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
        this.company = company;
    }

    public void update(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void update(boolean requiredFrontSeat) {
        this.requiredFrontSeat = requiredFrontSeat;
    }

    /** 케어 정보를 처음 붙인다. 이미 있으면 그대로 돌려준다 — 호출부에서 존재 여부를 또 따지지 않게. */
    public ElderCareProfile careProfileOrCreate() {
        if (careProfile == null) {
            careProfile = new ElderCareProfile(this);
        }
        return careProfile;
    }
}