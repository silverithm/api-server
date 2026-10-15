package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.entity.VoiceMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 본 서비스 고충·건의함(voice_messages)을 건수 계산용으로만 읽는다.
 * 제목·본문·작성자·답변 글은 열 자체를 가져오지 않는다(투영) — 실험실로 내용이 들어오지 않게 한다.
 */
public interface LabGrievanceRepository extends JpaRepository<VoiceMessage, Long> {

    @Query("SELECT v.status AS status, v.isAnonymous AS anonymous, v.createdAt AS createdAt, v.repliedAt AS repliedAt "
            + "FROM VoiceMessage v WHERE v.company.id = :companyId AND v.type = :type "
            + "AND v.createdAt >= :from AND v.createdAt < :toExclusive")
    List<Row> findRows(@Param("companyId") Long companyId, @Param("type") VoiceMessage.VoiceType type,
                       @Param("from") LocalDateTime from,
                       @Param("toExclusive") LocalDateTime toExclusive);

    interface Row {
        VoiceMessage.VoiceStatus getStatus();

        boolean getAnonymous();

        LocalDateTime getCreatedAt();

        LocalDateTime getRepliedAt();
    }
}
