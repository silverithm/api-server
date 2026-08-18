package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalRequestViewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRequestViewerRepository extends JpaRepository<ApprovalRequestViewer, Long> {

    List<ApprovalRequestViewer> findByApprovalRequestId(Long approvalRequestId);

    void deleteByApprovalRequestId(Long approvalRequestId);
}
