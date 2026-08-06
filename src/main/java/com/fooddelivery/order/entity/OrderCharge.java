package com.fooddelivery.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.order.enums.ChargeEntityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_charges")
@lombok.Getter
@lombok.Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCharge {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    @ToString.Exclude
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private ChargeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type")
    private ChargeEntityType payerType;

    @Column(name = "payer_id")
    private UUID payerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type")
    private ChargeEntityType payeeType;

    @Column(name = "payee_id")
    private UUID payeeId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "description")
    private String description;
}
