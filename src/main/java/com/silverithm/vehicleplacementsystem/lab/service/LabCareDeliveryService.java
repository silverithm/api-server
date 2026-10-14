package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareDeliveryDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareDelivery;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareDeliveryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 제공대장 — 월별 급여제공기록지를 누구에게 어떻게 전달했는지. 어르신·월당 1건. */
@Service
public class LabCareDeliveryService {

    private static final Set<String> METHODS = Set.of("VISIT", "MAIL", "FAX", "ELECTRONIC");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabCareDeliveryRepository deliveries;
    private final TransactionTemplate tx;

    public LabCareDeliveryService(LabAccessService access, LabElderDirectory elders,
                                  LabCareDeliveryRepository deliveries, PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.deliveries = deliveries;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public List<LabCareDeliveryDto.View> listByMonth(String month) {
        Long companyId = access.requireCompanyId();
        String ym = LabValidation.month(month, "월");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return deliveries.findByCompanyIdAndRecordMonth(companyId, ym).stream()
                .map(d -> toView(d, names.getOrDefault(d.getElderlyId(), "")))
                .sorted(Comparator.comparing(LabCareDeliveryDto.View::elderlyName))
                .toList();
    }

    public LabCareDeliveryDto.View upsert(LabCareDeliveryDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("제공 내역을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        String month = LabValidation.month(input.month(), "월");
        var deliveredOn = LabValidation.required(input.deliveredOn(), "전달일");
        String method = LabValidation.oneOf(input.method(), METHODS, "전달 방법");
        String recipient = LabValidation.text(input.recipient(), 50, "받은 사람");
        String note = LabValidation.text(input.note(), 200, "메모");

        LabCareDelivery saved = LabValidation.upsertWithRetry(tx, () -> {
            LabCareDelivery delivery = deliveries.findByElderlyIdAndRecordMonth(input.elderlyId(), month)
                    .orElseGet(() -> new LabCareDelivery(companyId, input.elderlyId(), month));
            delivery.fill(deliveredOn, method, recipient, note);
            return deliveries.saveAndFlush(delivery);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabCareDelivery delivery = deliveries.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("제공 내역을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        deliveries.delete(delivery);
    }

    private static LabCareDeliveryDto.View toView(LabCareDelivery d, String elderlyName) {
        return new LabCareDeliveryDto.View(d.getId(), d.getElderlyId(), elderlyName, d.getRecordMonth(),
                d.getDeliveredOn(), d.getMethod(), d.getRecipient(), d.getNote());
    }
}
