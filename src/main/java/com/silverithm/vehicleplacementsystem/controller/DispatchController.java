package com.silverithm.vehicleplacementsystem.controller;


import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.RequestDispatchDTO;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class DispatchController {

    @Autowired
    private DispatchService dispatchService;

    // RESTful API endpoint
    @PostMapping("/api/v1/dispatch")
    public List<AssignmentResponseDTO> dispatch(@RequestBody RequestDispatchDTO requestDispatchDTO) throws Exception {
        return dispatchService.getOptimizedAssignments(requestDispatchDTO);
    }


}
