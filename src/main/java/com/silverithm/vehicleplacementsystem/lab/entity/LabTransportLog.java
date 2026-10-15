package com.silverithm.vehicleplacementsystem.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 송영 운행일지 1건(고시 제34조 제4항) — 날짜 x 등원/하원 x 차량. 탑승자는 어르신 id가 든 JSON 열이다. */
@Entity
@Table(name = "lab_transport_logs", uniqueConstraints = @UniqueConstraint(name = "uk_lab_transport_logs_run",
        columnNames = {"company_id", "run_date", "direction", "vehicle_no"}))
@Getter
@NoArgsConstructor
public class LabTransportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    /** PICKUP | DROPOFF */
    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;

    @Column(name = "driver_member_id", nullable = false)
    private Long driverMemberId;

    @Column(name = "escort_member_id")
    private Long escortMemberId;

    @Column(name = "depart_time")
    private LocalTime departTime;

    @Column(name = "arrive_time")
    private LocalTime arriveTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "riders", nullable = false)
    private String riders;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "writer_name", length = 50)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LabTransportLog(Long companyId, LocalDate runDate, String direction, String vehicleNo) {
        this.companyId = companyId;
        this.runDate = runDate;
        this.direction = direction;
        this.vehicleNo = vehicleNo;
    }

    public void fill(Long driverMemberId, Long escortMemberId, LocalTime departTime, LocalTime arriveTime,
                     String riders, String note, String writerName) {
        this.driverMemberId = driverMemberId;
        this.escortMemberId = escortMemberId;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
        this.riders = riders;
        this.note = note;
        this.writerName = writerName;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
