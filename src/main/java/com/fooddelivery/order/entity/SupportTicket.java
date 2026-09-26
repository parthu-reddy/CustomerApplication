package com.fooddelivery.order.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "support_tickets", indexes = {
    @Index(name = "idx_support_ticket_status_created", columnList = "status, created_at"),
    @Index(name = "idx_support_ticket_order_customer_status", columnList = "order_id, customer_id, status")
})
public class SupportTicket {

    @Version
    @Column(name = "version")
    private Long version;

    public enum TicketStatus {
        OPEN, IN_REVIEW, RESOLVED, REJECTED
    }

    @Id
    @Column(name = "id")
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private UUID customerId;

    @Column(name = "reason", nullable = false, length = 2000)
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "created_at", nullable = false)
    @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @Column(name = "requested_refund_items", columnDefinition = "TEXT")
    private String requestedRefundItems;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "restaurant_comments", length = 2000)
    private String restaurantComments;

    @Column(name = "rider_comments", length = 2000)
    private String riderComments;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public UUID getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(UUID chatSessionId) { this.chatSessionId = chatSessionId; }

    public String getRequestedRefundItems() { return requestedRefundItems; }
    public void setRequestedRefundItems(String requestedRefundItems) { this.requestedRefundItems = requestedRefundItems; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getRestaurantComments() { return restaurantComments; }
    public void setRestaurantComments(String restaurantComments) { this.restaurantComments = restaurantComments; }

    public String getRiderComments() { return riderComments; }
    public void setRiderComments(String riderComments) { this.riderComments = riderComments; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
// @Getter
