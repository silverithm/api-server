package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleCategory category = ScheduleCategory.OTHER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_id")
    private ScheduleLabel label;

    private String location;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalTime startTime;

    @Column(nullable = false)
    private LocalDate endDate;

    private LocalTime endTime;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAllDay = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean sendNotification = false;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ScheduleParticipant> participants = new ArrayList<>();

    @Column(nullable = false)
    private String authorId;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isAllDay == null) {
            isAllDay = false;
        }
        if (category == null) {
            category = ScheduleCategory.OTHER;
        }
        if (sendNotification == null) {
            sendNotification = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(String title, String content, ScheduleCategory category,
                      ScheduleLabel label, String location, LocalDate startDate,
                      LocalTime startTime, LocalDate endDate, LocalTime endTime,
                      Boolean isAllDay, Boolean sendNotification) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (category != null) {
            this.category = category;
        }
        this.label = label; // can be null to remove label
        if (location != null) {
            this.location = location;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        this.startTime = startTime;
        if (endDate != null) {
            this.endDate = endDate;
        }
        this.endTime = endTime;
        if (isAllDay != null) {
            this.isAllDay = isAllDay;
        }
        if (sendNotification != null) {
            this.sendNotification = sendNotification;
        }
    }
}