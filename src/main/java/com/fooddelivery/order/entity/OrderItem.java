package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderItem {
    @Id
    @Column(name = "id")
    private UUID id;
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "menu_item_id")
    private UUID menuItemId;
    @Column(name = "name")
    private String name;
    @Column(name = "quantity")
    private Integer quantity;
    @Column(name = "price")
    private BigDecimal price;
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    // @Builder.Default is load-bearing: without it Lombok drops the initializer and every
    // builder-created item is persisted with refunded_quantity NULL, not 0 -- the column's
    // DEFAULT never applies because JPA writes an explicit null. Two call sites currently
    // compensate with `!= null ? ... : 0`; this makes the field mean what it says instead.
    @lombok.Builder.Default
    @Column(name = "refunded_quantity")
    private Integer refundedQuantity = 0;


    

    

    
}
