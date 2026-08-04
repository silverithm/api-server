package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.VoiceMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoiceMessageRepository extends JpaRepository<VoiceMessage, Long> {

    List<VoiceMessage> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<VoiceMessage> findByCompanyIdAndTypeOrderByCreatedAtDesc(Long companyId, VoiceMessage.VoiceType type);

    List<VoiceMessage> findByAuthorTypeAndAuthorRefIdOrderByCreatedAtDesc(
            ApprovalStep.ApproverType authorType, Long authorRefId);

    @Modifying
    @Query("DELETE FROM VoiceMessage v WHERE v.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);
}
