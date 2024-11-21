package com.silverithm.vehicleplacementsystem.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDetailDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.RequestDispatchDTO;
import com.silverithm.vehicleplacementsystem.service.DispatchHistoryService;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import com.silverithm.vehicleplacementsystem.service.DispatchServiceV2;
import com.silverithm.vehicleplacementsystem.service.DispatchServiceV3;
import com.silverithm.vehicleplacementsystem.service.DispatchServiceV4;
import com.silverithm.vehicleplacementsystem.service.SSEService;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class DispatchController {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private DispatchServiceV2 dispatchServiceV2;

    @Autowired
    private DispatchServiceV3 dispatchServiceV3;

    @Autowired
    private DispatchServiceV4 dispatchServiceV4;

    @Autowired
    private DispatchServiceV4 dispatchServiceV5;

    @Autowired
    private SSEService sseService;

    @Autowired
    private DispatchHistoryService dispatchHistoryService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Qualifier("dispatchQueue")
    @Autowired
    private Queue dispatchQueue;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/api/v1/dispatch")
    public ResponseEntity<String> dispatch(@RequestBody RequestDispatchDTO requestDispatchDTO) {

        try {
            String jobId = UUID.randomUUID().toString();

            Message message = MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(requestDispatchDTO))
                    .setHeader("jobId", jobId)
                    .build();

            rabbitTemplate.convertAndSend(dispatchQueue.getName(), message);

            return ResponseEntity.accepted()
                    .body("jobId : " + jobId + " 배차 요청 성공");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("배차 요청 실패");
        }

    }

    @RabbitListener(queues = "dispatch-response-queue")
    public void handleDispatchResponse(Message message) {
        try {
            List<AssignmentResponseDTO> result = objectMapper.readValue(message.getBody(),
                    new TypeReference<List<AssignmentResponseDTO>>() {
                    });

            dispatchHistoryService.saveDispatchResult(result);
        } catch (Exception e) {
            log.error("배차 응답 처리 중 오류 발생: ", e);
            String jobId = message.getMessageProperties().getHeaders().get("jobId").toString();
            sseService.notifyError(jobId);
        }
    }


    @GetMapping("/api/v1/history")
    public List<DispatchHistoryDTO> getHistories() {
        return dispatchHistoryService.getDispatchHistories();
    }

    @GetMapping("/api/v1/history/{id}")
    public DispatchHistoryDetailDTO getHistoryDetail(@PathVariable Long id) throws JsonProcessingException {
        return dispatchHistoryService.getDispatchDetail(id);
    }


}
