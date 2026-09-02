package com.fooddelivery.order.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.order.entity.SupportTicket;
import com.fooddelivery.order.repository.SupportTicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/restaurants/outlets")
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class RestaurantSupportTicketController {

    private final SupportTicketRepository supportTicketRepository;

    @GetMapping("/{outletId}/refund-requests")
    public ResponseEntity<ApiResponse<List<SupportTicket>>> getActiveRefundRequests(
            @PathVariable UUID outletId) {
        log.info("Fetching active refund requests for outlet {}", outletId);
        try {
            List<SupportTicket> activeTickets = supportTicketRepository.findByRestaurantIdAndStatus(outletId, SupportTicket.TicketStatus.OPEN);
            return ResponseEntity.ok(ApiResponse.success(activeTickets, "Successfully fetched active refund requests"));
        } catch (Exception e) {
            log.error("Failed to fetch refund requests for outlet {}", outletId, e);
            return ResponseEntity.status(500).body(ApiResponse.<List<SupportTicket>>error("Failed to fetch refund requests"));
        }
    }
}
