package com.silverithm.vehicleplacementsystem.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDetailDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<String> dispatch(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody RequestDispatchDTO requestDispatchDTO) {

        try {
            String jobId = UUID.randomUUID().toString();

            Message message = MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(requestDispatchDTO))
                    .setHeader("jobId", jobId)
                    .setHeader("username", userDetails.getUsername())
                    .build();

            rabbitTemplate.convertAndSend(dispatchQueue.getName(), message);

            return ResponseEntity.accepted()
                    .body(jobId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("배차 요청 실패");
        }

    }

    @RabbitListener(queues = "dispatch-response-queue")
    public void handleDispatchResponse(Message message) {
        try {
            String username = message.getMessageProperties().getHeaders().get("username").toString();

            List<AssignmentResponseDTO> result = objectMapper.readValue(message.getBody(),
                    new TypeReference<List<AssignmentResponseDTO>>() {
                    });

            dispatchHistoryService.saveDispatchResult(result, username);
        } catch (Exception e) {
            log.error("배차 응답 처리 중 오류 발생: ", e);
            String jobId = message.getMessageProperties().getHeaders().get("jobId").toString();
            sseService.notifyError(jobId);
        }
    }


    @GetMapping("/api/v1/history")
    public Page<DispatchHistoryDTO> getHistories(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return dispatchHistoryService.getDispatchHistories(userDetails,pageable);
    }

    @GetMapping("/api/v1/history/{id}")
    public DispatchHistoryDetailDTO getHistoryDetail(@PathVariable Long id) throws JsonProcessingException {
        return dispatchHistoryService.getDispatchDetail(id);
    }

    @DeleteMapping("/api/v1/history/{id}")
    public ResponseEntity<Long> deleteHistory(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return dispatchHistoryService.deleteHistory(id, userDetails);
    }


}
