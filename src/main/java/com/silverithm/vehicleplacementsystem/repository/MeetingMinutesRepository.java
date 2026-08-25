package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.MeetingMinutes;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, Long> {

    /** 목록은 참석자(서명 현황 배지)까지 한 번에 가져온다 */
    @EntityGraph(attributePaths = "attendees")
    List<MeetingMinutes> findByCompanyIdOrderByMeetingStartAtDesc(Long companyId);
}
