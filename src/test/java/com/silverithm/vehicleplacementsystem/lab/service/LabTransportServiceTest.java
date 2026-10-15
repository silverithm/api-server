package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.lab.dto.LabTransportDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabGuideDeliveryRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabTransportLogRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabTransportVehicleRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabTransportServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Autowired private LabTransportService service;
    @Autowired private LabTransportLogRepository logs;
    @Autowired private LabTransportVehicleRepository vehicles;
    @Autowired private LabGuideDeliveryRepository deliveries;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member driver;
    private Member escort;
    private Elderly kim;
    private Elderly lee;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        driver = LabTestFixtures.member(em, forest, "운전원", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        escort = LabTestFixtures.member(em, forest, "동승자", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        lee = LabTestFixtures.elder(em, forest, "이어르신");
        access.companyId = forest.getId();
    }

    private static LabTransportDto.RiderInput rider(Long elderlyId, boolean boarded, String board, String alight) {
        return new LabTransportDto.RiderInput(elderlyId, boarded, board, alight, null);
    }

    private LabTransportDto.LogInput log(String direction, String vehicleNo, Long driverId, Long escortId,
                                         String depart, String arrive, LabTransportDto.RiderInput... riders) {
        return new LabTransportDto.LogInput(DAY, direction, vehicleNo, driverId, escortId, depart, arrive,
                List.of(riders), null);
    }

    private LabTransportDto.LogInput simple(String direction, String vehicleNo) {
        return log(direction, vehicleNo, driver.getId(), escort.getId(), "08:00", "09:10",
                rider(kim.getId(), true, "08:20", "09:05"));
    }

    private static LabTransportDto.VehicleInput vehicle(String no) {
        return new LabTransportDto.VehicleInput(no, "카니발", "한화손해보험", "P-1", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), true, null, null);
    }

    // 날짜 x 등원/하원 x 차량마다 한 건 — 다시 저장하면 덮어쓰고, 이름·시각이 'HH:mm'으로 채워진다
    @Test
    void logUpsertByDateDirectionVehicle() {
        LabTransportDto.LogView first = service.upsertLog(simple("PICKUP", "12가3456"));
        assertThat(first.driverName()).isEqualTo("운전원");
        assertThat(first.escortName()).isEqualTo("동승자");
        assertThat(first.departTime()).isEqualTo("08:00");
        assertThat(first.arriveTime()).isEqualTo("09:10");
        assertThat(first.riders()).singleElement().satisfies(r -> {
            assertThat(r.elderlyName()).isEqualTo("김어르신");
            assertThat(r.boarded()).isTrue();
            assertThat(r.boardTime()).isEqualTo("08:20");
            assertThat(r.alightTime()).isEqualTo("09:05");
        });
        assertThat(first.writerName()).isEqualTo("숲속 관리자");

        // 같은 키로 다시 저장 — 같은 행, 탑승자·동승자가 통째로 바뀐다
        LabTransportDto.LogView second = service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, null, null,
                rider(kim.getId(), false, null, null), rider(lee.getId(), true, "08:30", null)));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(logs.count()).isEqualTo(1);
        assertThat(second.escortMemberId()).isNull();
        assertThat(second.escortName()).isNull();
        assertThat(second.departTime()).isNull();
        assertThat(second.riders()).extracting(LabTransportDto.Rider::elderlyName).containsExactly("김어르신", "이어르신");
        assertThat(second.riders().get(0).boarded()).isFalse();

        // 방향·차량이 다르면 다른 행
        service.upsertLog(simple("DROPOFF", "12가3456"));
        service.upsertLog(simple("PICKUP", "34나5678"));
        assertThat(logs.count()).isEqualTo(3);

        // 날짜·달 조회 — 등원이 하원보다 먼저, 차량번호 순
        List<LabTransportDto.LogView> day = service.listLogs("2026-03-10", null);
        assertThat(day).extracting(LabTransportDto.LogView::direction, LabTransportDto.LogView::vehicleNo)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PICKUP", "12가3456"),
                        org.assertj.core.groups.Tuple.tuple("PICKUP", "34나5678"),
                        org.assertj.core.groups.Tuple.tuple("DROPOFF", "12가3456"));
        assertThat(service.listLogs(null, "2026-03")).hasSize(3);
        assertThat(service.listLogs(null, "2026-02")).isEmpty();
        assertThat(service.listLogs("2026-03-11", null)).isEmpty();
        assertRejected(() -> service.listLogs(null, null), HttpStatus.BAD_REQUEST, "date 또는 month");
        assertRejected(() -> service.listLogs("2026-3-1", null), HttpStatus.BAD_REQUEST, "YYYY-MM-DD");
        assertRejected(() -> service.listLogs(null, "2026-13"), HttpStatus.BAD_REQUEST, "YYYY-MM");

        // 입력 값: 방향·차량번호·시각 형식·도착이 출발보다 빠름·미래·탑승자 0명·31명
        assertRejected(() -> service.upsertLog(simple("BOTH", "12가3456")), HttpStatus.BAD_REQUEST, "등원/하원");
        assertRejected(() -> service.upsertLog(simple("PICKUP", "  ")), HttpStatus.BAD_REQUEST, "차량번호");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, "8:00", null,
                rider(kim.getId(), true, null, null))), HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, "09:00", "08:59",
                rider(kim.getId(), true, null, null))), HttpStatus.BAD_REQUEST, "도착 시각");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, null, null,
                rider(kim.getId(), true, "25:00", null))), HttpStatus.BAD_REQUEST, "탑승 시각");
        assertRejected(() -> service.upsertLog(new LabTransportDto.LogInput(LabValidation.seoulToday().plusDays(1),
                "PICKUP", "12가3456", driver.getId(), null, null, null, List.of(rider(kim.getId(), true, null, null)),
                null)), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.upsertLog(new LabTransportDto.LogInput(DAY, "PICKUP", "12가3456", driver.getId(),
                null, null, null, List.of(), null)), HttpStatus.BAD_REQUEST, "탑승 어르신");
        assertRejected(() -> service.upsertLog(new LabTransportDto.LogInput(DAY, "PICKUP", "12가3456", driver.getId(),
                null, null, null, java.util.stream.LongStream.rangeClosed(1, 31)
                        .mapToObj(i -> rider(i, true, null, null)).toList(), null)), HttpStatus.BAD_REQUEST, "최대 30명");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", null, null, null, null,
                rider(kim.getId(), true, null, null))), HttpStatus.BAD_REQUEST, "운전원");
        // 거절된 저장은 기존 값을 바꾸지 않는다
        assertThat(service.listLogs("2026-03-10", null).get(0).riders()).hasSize(2);
        // 삭제 — 다른 기관은 못 지운다
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertRejected(() -> service.deleteLog(first.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertThat(service.listLogs(null, "2026-03")).isEmpty();
        access.companyId = forest.getId();
        service.deleteLog(first.id());
        assertThat(logs.count()).isEqualTo(2);
    }

    // 운전원과 동승자가 같은 사람이면 400 — 수정으로 같게 만드는 것도 막는다
    @Test
    void driverEscortMustDiffer() {
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), driver.getId(), null, null,
                rider(kim.getId(), true, null, null))), HttpStatus.BAD_REQUEST, "다른 사람");
        assertThat(logs.count()).isZero();
        LabTransportDto.LogView saved = service.upsertLog(simple("PICKUP", "12가3456"));
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", escort.getId(), escort.getId(), null, null,
                rider(kim.getId(), true, null, null))), HttpStatus.BAD_REQUEST, "다른 사람");
        assertThat(logs.findById(saved.id()).orElseThrow().getDriverMemberId()).isEqualTo(driver.getId());
        // 동승자 없이(null) 운행하는 것은 허용
        assertThat(service.upsertLog(log("DROPOFF", "12가3456", driver.getId(), null, null, null,
                rider(kim.getId(), true, null, null))).escortName()).isNull();
    }

    // 운전원·동승자·탑승자는 모두 같은 기관 — 아니면 404이고 일지는 저장되지 않는다
    @Test
    void riderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member outsider = LabTestFixtures.member(em, other, "외부직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);
        Elderly outElder = LabTestFixtures.elder(em, other, "외부어르신");

        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, null, null,
                rider(kim.getId(), true, null, null), rider(outElder.getId(), true, null, null))),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, null, null,
                rider(999_999L, true, null, null))), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", outsider.getId(), null, null, null,
                rider(kim.getId(), true, null, null))), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), outsider.getId(), null, null,
                rider(kim.getId(), true, null, null))), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.upsertLog(log("PICKUP", "12가3456", driver.getId(), null, null, null,
                rider(kim.getId(), true, null, null), rider(kim.getId(), false, null, null))),
                HttpStatus.BAD_REQUEST, "두 번");
        assertThat(logs.count()).isZero();

        // 제공 기록도 같은 규칙 — 한 명이라도 다른 기관이면 하나도 만들지 않는다
        assertRejected(() -> service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TRANSPORT_RULES", DAY,
                "FACE", null, List.of(kim.getId(), outElder.getId()))), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(deliveries.count()).isZero();
        assertRejected(() -> service.listDeliveries("TIMETABLE", outElder.getId()), HttpStatus.NOT_FOUND,
                "어르신을 찾을 수 없습니다");
    }

    // 차량번호는 기관 안에서 하나 — 만들기·수정 모두 409, 다른 기관은 같은 번호를 쓸 수 있다
    @Test
    void vehicleNoDuplicateRejected() {
        LabTransportDto.VehicleView a = service.createVehicle(vehicle("12가3456"));
        assertThat(a.comprehensive()).isTrue();
        assertThat(a.active()).isTrue();
        LabTransportDto.VehicleView b = service.createVehicle(vehicle("34나5678"));
        assertRejected(() -> service.createVehicle(vehicle(" 12가3456 ")), HttpStatus.CONFLICT, "이미 등록된");
        assertRejected(() -> service.updateVehicle(b.id(), vehicle("12가3456")), HttpStatus.CONFLICT, "이미 등록된");
        assertThat(vehicles.count()).isEqualTo(2);
        // 자기 번호 그대로 수정은 통과, 보험 내용이 바뀐다
        LabTransportDto.VehicleView renewed = service.updateVehicle(a.id(), new LabTransportDto.VehicleInput(
                "12가3456", "카니발", "삼성화재", "P-2", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), false,
                false, "책임보험만"));
        assertThat(renewed.id()).isEqualTo(a.id());
        assertThat(renewed.insurer()).isEqualTo("삼성화재");
        assertThat(renewed.comprehensive()).isFalse();
        assertThat(renewed.active()).isFalse();
        // 쓰는 차량이 먼저 나온다
        assertThat(service.listVehicles()).extracting(LabTransportDto.VehicleView::vehicleNo)
                .containsExactly("34나5678", "12가3456");

        // 만기가 시작보다 앞서면 400, 차량번호는 필수
        assertRejected(() -> service.createVehicle(new LabTransportDto.VehicleInput("56다7890", null, null, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 30), true, null, null)), HttpStatus.BAD_REQUEST,
                "만기일");
        assertRejected(() -> service.createVehicle(vehicle(null)), HttpStatus.BAD_REQUEST, "차량번호");
        assertRejected(() -> service.createVehicle(vehicle("가".repeat(21))), HttpStatus.BAD_REQUEST, "차량번호");

        // 다른 기관은 같은 번호를 쓸 수 있고, 내 차량을 고치거나 지우지 못한다
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        service.createVehicle(vehicle("12가3456"));
        assertRejected(() -> service.updateVehicle(a.id(), vehicle("99라9999")), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertRejected(() -> service.deleteVehicle(a.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        access.companyId = forest.getId();
        service.deleteVehicle(a.id());
        assertThat(service.listVehicles()).hasSize(1);
        // 지운 번호는 다시 등록할 수 있다
        service.createVehicle(vehicle("12가3456"));
    }

    // 제공 기록 한 번에 여러 명 — 어르신마다 한 건, 이름이 채워지고 kind·elderlyId로 거른다
    @Test
    void deliveryBatchCreatesPerElder() {
        List<LabTransportDto.DeliveryView> created = service.createDeliveries(new LabTransportDto.DeliveryBatchInput(
                "TRANSPORT_RULES", DAY, "PAPER", "수칙 안내문", List.of(kim.getId(), lee.getId(), kim.getId())));
        assertThat(created).extracting(LabTransportDto.DeliveryView::elderlyName).containsExactly("김어르신", "이어르신");
        assertThat(created).extracting(LabTransportDto.DeliveryView::method).containsOnly("PAPER");
        assertThat(deliveries.count()).isEqualTo(2);

        service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE", DAY.plusDays(5), "SMS", null,
                List.of(kim.getId())));
        service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TRANSPORT_RULES", DAY.plusDays(2), "FACE",
                null, List.of(kim.getId())));
        assertThat(service.listDeliveries("TRANSPORT_RULES", null)).extracting(LabTransportDto.DeliveryView::deliveredOn)
                .containsExactly(DAY.plusDays(2), DAY, DAY);
        assertThat(service.listDeliveries("TRANSPORT_RULES", lee.getId())).hasSize(1);
        assertThat(service.listDeliveries("TIMETABLE", null)).hasSize(1);
        assertRejected(() -> service.listDeliveries(null, null), HttpStatus.BAD_REQUEST, "kind");
        assertRejected(() -> service.listDeliveries("MENU", null), HttpStatus.BAD_REQUEST, "제공 종류");

        // 입력 검증 — 거절되면 하나도 만들지 않는다
        long before = deliveries.count();
        assertRejected(() -> service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE", DAY, "FAX",
                null, List.of(kim.getId()))), HttpStatus.BAD_REQUEST, "제공 방법");
        assertRejected(() -> service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE", DAY, "SNS",
                null, List.of())), HttpStatus.BAD_REQUEST, "1명 이상");
        assertRejected(() -> service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE", DAY, "SNS",
                null, java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList())), HttpStatus.BAD_REQUEST,
                "최대 100명");
        assertRejected(() -> service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE",
                LabValidation.seoulToday().plusDays(1), "SNS", null, List.of(kim.getId()))), HttpStatus.BAD_REQUEST,
                "오늘 이후");
        assertRejected(() -> service.createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE", DAY, "SNS",
                "가".repeat(201), List.of(kim.getId()))), HttpStatus.BAD_REQUEST, "메모");
        assertThat(deliveries.count()).isEqualTo(before);

        // 삭제 — 다른 기관은 못 지운다
        Long id = created.get(0).id();
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertRejected(() -> service.deleteDelivery(id), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertThat(service.listDeliveries("TRANSPORT_RULES", null)).isEmpty();
        access.companyId = forest.getId();
        service.deleteDelivery(id);
        assertThat(deliveries.count()).isEqualTo(before - 1);
    }
}
