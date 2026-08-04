package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 데모 테넌트(Company)와 그에 딸린 전 도메인 데이터를 통째로 삭제한다.
 *
 * company_id FK 대부분이 ON DELETE RESTRICT라 자식 테이블을 먼저 지워야 한다.
 * 손자 테이블(approval_steps, notice_comments/readers, schedule_participants/tasks,
 * chat_* 등)은 운영 DB에선 ON DELETE CASCADE가 걸려 있지만, CASCADE 없는 스키마에서도
 * 동작하도록 전부 명시적으로 역순 삭제한다.
 */
@Slf4j
@Service
public class DemoTeardownService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void deleteDemoTenant(Company company) {
        Long companyId = company.getId();
        if (!company.isDemoCompany()) {
            log.warn("[Demo] 데모가 아닌 회사 삭제 시도 차단: companyId={}", companyId);
            return;
        }

        // 알림은 FK가 없어 순서 무관하지만, member id를 알아야 하므로 members 삭제 전에 정리
        List<String> recipientIds = em.createQuery(
                        "SELECT CAST(m.id AS string) FROM Member m WHERE m.company.id = :companyId", String.class)
                .setParameter("companyId", companyId).getResultList();
        if (!recipientIds.isEmpty()) {
            em.createQuery("DELETE FROM Notification n WHERE n.recipientUserId IN :ids")
                    .setParameter("ids", recipientIds).executeUpdate();
        }

        // 채팅: 메시지 리액션/읽음 → 메시지 → 참가자 → 방
        deleteWhereParentIn("ChatMessageReaction", "message",
                "SELECT msg.id FROM ChatMessage msg WHERE msg.chatRoom.company.id = :companyId", companyId);
        deleteWhereParentIn("ChatMessageRead", "message",
                "SELECT msg.id FROM ChatMessage msg WHERE msg.chatRoom.company.id = :companyId", companyId);
        // 답글(reply_to) 자기참조 FK를 먼저 끊는다.
        // JPQL로 쓰면 MySQL이 거부하는 자기 테이블 서브쿼리로 번역되므로 JOIN 네이티브 쿼리를 쓴다.
        em.createNativeQuery("UPDATE chat_messages m JOIN chat_rooms r ON m.chat_room_id = r.id "
                        + "SET m.reply_to_id = NULL WHERE r.company_id = :companyId")
                .setParameter("companyId", companyId).executeUpdate();
        deleteWhereParentIn("ChatMessage", "chatRoom",
                "SELECT r.id FROM ChatRoom r WHERE r.company.id = :companyId", companyId);
        deleteWhereParentIn("ChatParticipant", "chatRoom",
                "SELECT r.id FROM ChatRoom r WHERE r.company.id = :companyId", companyId);
        deleteByCompany("ChatRoom", companyId);

        // 전자결재: 결재선 → 요청 → 템플릿
        deleteWhereParentIn("ApprovalStep", "approvalRequest",
                "SELECT ar.id FROM ApprovalRequest ar WHERE ar.company.id = :companyId", companyId);
        deleteByCompany("ApprovalRequest", companyId);
        deleteByCompany("ApprovalTemplate", companyId);

        // 공지: 댓글/읽음 → 공지
        deleteWhereParentIn("NoticeComment", "notice",
                "SELECT n.id FROM Notice n WHERE n.company.id = :companyId", companyId);
        deleteWhereParentIn("NoticeReader", "notice",
                "SELECT n.id FROM Notice n WHERE n.company.id = :companyId", companyId);
        deleteByCompany("Notice", companyId);

        // 일정: 참석자/할일 → 일정 → 라벨
        deleteWhereParentIn("ScheduleParticipant", "schedule",
                "SELECT s.id FROM Schedule s WHERE s.company.id = :companyId", companyId);
        deleteWhereParentIn("ScheduleTask", "schedule",
                "SELECT s.id FROM Schedule s WHERE s.company.id = :companyId", companyId);
        deleteByCompany("Schedule", companyId);
        deleteByCompany("ScheduleLabel", companyId);
        deleteByCompany("EmployeeAttendance", companyId);
        deleteByCompany("ElderAttendance", companyId);
        deleteByCompany("VacationRequest", companyId);
        deleteByCompany("VacationLimit", companyId);
        deleteByCompany("MemberJoinRequest", companyId);
        deleteByCompany("VoiceMessage", companyId);
        // member_permissions는 @ElementCollection이라 엔티티가 없어 네이티브로 지운다
        em.createNativeQuery("DELETE FROM member_permissions WHERE member_id IN "
                        + "(SELECT id FROM members WHERE company_id = :companyId)")
                .setParameter("companyId", companyId).executeUpdate();
        deleteByCompany("Member", companyId);
        deleteByCompany("Elderly", companyId);
        deleteByCompany("Employee", companyId);
        deleteByCompany("Position", companyId);
        em.createQuery("DELETE FROM DocumentNumberCounter d WHERE d.companyId = :companyId")
                .setParameter("companyId", companyId).executeUpdate();
        em.createQuery("DELETE FROM AuditLog a WHERE a.companyId = :companyId")
                .setParameter("companyId", companyId).executeUpdate();

        em.createQuery("DELETE FROM Subscription s WHERE s.user.id IN "
                        + "(SELECT u.id FROM AppUser u WHERE u.company.id = :companyId)")
                .setParameter("companyId", companyId).executeUpdate();
        em.createQuery("DELETE FROM FreeSubscriptionHistory f WHERE f.user.id IN "
                        + "(SELECT u.id FROM AppUser u WHERE u.company.id = :companyId)")
                .setParameter("companyId", companyId).executeUpdate();
        em.createQuery("DELETE FROM PaymentFailureLog p WHERE p.user.id IN "
                        + "(SELECT u.id FROM AppUser u WHERE u.company.id = :companyId)")
                .setParameter("companyId", companyId).executeUpdate();
        deleteByCompany("AppUser", companyId);

        em.createQuery("DELETE FROM Company c WHERE c.id = :companyId")
                .setParameter("companyId", companyId).executeUpdate();

        log.info("[Demo] 데모 테넌트 삭제 완료: companyId={}, name={}", companyId, company.getName());
    }

    private void deleteByCompany(String entityName, Long companyId) {
        em.createQuery("DELETE FROM " + entityName + " e WHERE e.company.id = :companyId")
                .setParameter("companyId", companyId).executeUpdate();
    }

    private void deleteWhereParentIn(String entityName, String parentField, String parentIdSubquery, Long companyId) {
        em.createQuery("DELETE FROM " + entityName + " e WHERE e." + parentField + ".id IN (" + parentIdSubquery + ")")
                .setParameter("companyId", companyId).executeUpdate();
    }
}
