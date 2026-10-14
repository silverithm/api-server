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
import org.springframework.web.bind.annotation.RestController;

/** 운영위원 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/committee-members")
@RequiredArgsConstructor
public class LabCommitteeMemberController {

    private final LabCommitteeService service;

    @GetMapping
    public List<LabCommitteeDto.MemberView> list() {
        return service.listMembers();
    }

    @PostMapping
    public LabCommitteeDto.MemberView create(@RequestBody LabCommitteeDto.MemberInput input) {
        return service.createMember(input);
    }

    @PutMapping("/{id}")
    public LabCommitteeDto.MemberView update(@PathVariable Long id, @RequestBody LabCommitteeDto.MemberInput input) {
        return service.updateMember(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteMember(id);
        return ResponseEntity.noContent().build();
    }
}
