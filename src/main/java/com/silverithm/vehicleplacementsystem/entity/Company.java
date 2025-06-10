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
import java.util.List;
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

    @Column(name = "expose", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean expose = true;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "company_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "company_longitude"))
    })
    private Location companyAddress;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<AppUser> users;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<Employee> employees;

    public Company(String name, String addressName, Location companyAddress) {
        this.name = name;
        this.addressName = addressName;
        this.companyAddress = companyAddress;
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
}
