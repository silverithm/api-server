package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCommitteeDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCommitteeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영위원회 회의 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/committee-meetings")
@RequiredArgsConstructor
public class LabCommitteeMeetingController {

    private final LabCommitteeService service;

    @GetMapping
    public List<LabCommitteeDto.MeetingView> list(@RequestParam(required = false) String year) {
        return service.listMeetings(year);
    }

    @PostMapping
    public LabCommitteeDto.MeetingView create(@RequestBody LabCommitteeDto.MeetingInput input) {
        return service.createMeeting(input);
    }

    @PutMapping("/{id}")
    public LabCommitteeDto.MeetingView update(@PathVariable Long id,
                                              @RequestBody LabCommitteeDto.MeetingInput input) {
        return service.updateMeeting(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }
}
