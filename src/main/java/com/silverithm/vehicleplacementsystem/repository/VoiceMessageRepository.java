package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.VoiceMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoiceMessageRepository extends JpaRepository<VoiceMessage, Long> {

    List<VoiceMessage> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<VoiceMessage> findByCompanyIdAndTypeOrderByCreatedAtDesc(Long companyId, VoiceMessage.VoiceType type);

    List<VoiceMessage> findByAuthorTypeAndAuthorRefIdOrderByCreatedAtDesc(
            ApprovalStep.ApproverType authorType, Long authorRefId);
}
