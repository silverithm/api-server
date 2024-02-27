package com.silverithm.vehicleplacementsystem.controller;


import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.RequestDispatchDTO;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DispatchController {

    @Autowired
    private DispatchService dispatchService;

    // RESTful API endpoint
    @PostMapping("/dispatch")
    public List<Location> dispatch(@RequestBody RequestDispatchDTO requestDispatchDTO) {
        dispatchService.getOptimizedAssignments(requestDispatchDTO);
        return null;
    }

}
