package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Company;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 저장소 파일 경로가 특정 기관(company)에 귀속되는지 검증하기 위한 조회 모음.
 *
 * <p>업로드된 파일은 S3 키만 알면 누구나 내려받을 수 있는 구조였으므로(IDOR),
 * 다운로드·삭제 시 "이 경로가 요청자 기관의 레코드에서 참조되고 있는가"를 확인하는 데 사용한다.
 * 조회 대상은 파일 경로를 보관하는 모든 컬럼이다.
 *
 * <p>컬럼에 따라 상대 경로({@code approvals/x.pdf})와 절대 S3 URL이 섞여 저장돼 있으므로
 * 두 표기를 모두 후보로 받아 {@code IN} 으로 대조한다.
 */
public interface FileOwnershipRepository extends Repository<Company, Long> {

    /** 전자결재 첨부파일 */
    @Query("SELECT COUNT(a) > 0 FROM ApprovalRequest a "
            + "WHERE a.company.id = :companyId AND a.attachmentUrl IN :paths")
    boolean existsApprovalAttachment(@Param("companyId") Long companyId,
                                     @Param("paths") Collection<String> paths);

    /** 결재 양식 파일 */
    @Query("SELECT COUNT(t) > 0 FROM ApprovalTemplate t "
            + "WHERE t.company.id = :companyId AND t.fileUrl IN :paths")
    boolean existsTemplateFile(@Param("companyId") Long companyId,
                               @Param("paths") Collection<String> paths);

    /** 결재선 단계에 날인된 서명 이미지 */
    @Query("SELECT COUNT(s) > 0 FROM ApprovalStep s "
            + "WHERE s.approvalRequest.company.id = :companyId AND s.signatureUrl IN :paths")
    boolean existsApprovalStepSignature(@Param("companyId") Long companyId,
                                        @Param("paths") Collection<String> paths);

    /** 채팅 첨부파일 */
    @Query("SELECT COUNT(m) > 0 FROM ChatMessage m "
            + "WHERE m.chatRoom.company.id = :companyId AND m.fileUrl IN :paths")
    boolean existsChatFile(@Param("companyId") Long companyId,
                           @Param("paths") Collection<String> paths);

    /** 관리자 계정 서명 이미지 */
    @Query("SELECT COUNT(u) > 0 FROM AppUser u "
            + "WHERE u.company.id = :companyId AND u.signatureUrl IN :paths")
    boolean existsAdminSignature(@Param("companyId") Long companyId,
                                 @Param("paths") Collection<String> paths);

    /** 직원 계정 서명 이미지 */
    @Query("SELECT COUNT(m) > 0 FROM Member m "
            + "WHERE m.company.id = :companyId AND m.signatureUrl IN :paths")
    boolean existsMemberSignature(@Param("companyId") Long companyId,
                                  @Param("paths") Collection<String> paths);

    /** 기관 직인 이미지 */
    @Query("SELECT COUNT(c) > 0 FROM Company c "
            + "WHERE c.id = :companyId AND c.sealUrl IN :paths")
    boolean existsCompanySeal(@Param("companyId") Long companyId,
                              @Param("paths") Collection<String> paths);
}
