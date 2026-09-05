package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "refund_items")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class RefundItem {
    @Id
    @Column(name = "id")
    private UUID id;
    
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;
    
    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
