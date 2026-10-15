package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 욕구사정·급여제공계획 요청·응답. needs는 여섯 영역 객체, services는 {영역, 내용, 빈도} 배열이다. */
public final class LabCarePlanDto {

    private LabCarePlanDto() {
    }

    /**
     * 욕구사정 칸. 앞의 여섯은 1차부터, disease(질병상태)·oral(구강상태)·subjective(주관적 욕구)·resources(자원이용 욕구)·
     * summary(총평)·avoidFoods(기피식품)는 11차에 더했다. 옛 기록에는 새 키가 없어 읽을 때 ''로 돌려준다.
     */
    public record Needs(String physical, String cognition, String nutrition, String communication, String family,
                        String other, String disease, String oral, String subjective, String resources,
                        String summary, String avoidFoods) {

        /** 1~10차 호환 — 새 칸은 비운다 */
        public Needs(String physical, String cognition, String nutrition, String communication, String family,
                     String other) {
            this(physical, cognition, nutrition, communication, family, other, null, null, null, null, null, null);
        }
    }

    public record ServiceItem(String area, String content, String frequency) {
    }

    /** planYear는 Integer로 받아 32768 같은 값이 JSON 단계에서 깨지지 않고 400 문구로 돌아오게 한다 */
    public record Input(Long elderlyId, Integer planYear, LocalDate assessedOn, Needs needs, String goals,
                        List<ServiceItem> services, LocalDate guardianAgreedOn, String writerName,
                        Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, int planYear, LocalDate assessedOn,
                       Needs needs, String goals, List<ServiceItem> services, LocalDate guardianAgreedOn,
                       String writerName, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
