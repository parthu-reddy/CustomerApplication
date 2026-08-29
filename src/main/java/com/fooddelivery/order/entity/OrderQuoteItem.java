package com.fooddelivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One priced line of a quote.
 *
 * <p>The unit price is stored, not re-read at checkout: honouring the quoted total while building
 * the order lines from freshly fetched menu prices would let the line items sum to something other
 * than the amount charged.
 */
@Entity
@Table(name = "order_quote_items")
@lombok.Getter
@lombok.Setter
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderQuoteItem {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private OrderQuote quote;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(name = "name")
    private String name;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;
}
