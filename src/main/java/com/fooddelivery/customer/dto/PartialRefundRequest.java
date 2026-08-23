package com.fooddelivery.customer.dto;

import java.math.BigDecimal;

public class PartialRefundRequest {
    private BigDecimal amount;
    private String reason;
    private com.fooddelivery.common.enums.FaultType faultType = com.fooddelivery.common.enums.FaultType.UNKNOWN;

    
    public PartialRefundRequest() {
    }

    
    public BigDecimal getAmount() {
        return this.amount;
    }

    
    public String getReason() {
        return this.reason;
    }

    
    public com.fooddelivery.common.enums.FaultType getFaultType() {
        return this.faultType;
    }

    
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    
    public void setReason(final String reason) {
        this.reason = reason;
    }

    
    public void setFaultType(final com.fooddelivery.common.enums.FaultType faultType) {
        this.faultType = faultType;
    }

    @java.lang.Override
    
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof PartialRefundRequest)) return false;
        final PartialRefundRequest other = (PartialRefundRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$amount = this.getAmount();
        final java.lang.Object other$amount = other.getAmount();
        if (this$amount == null ? other$amount != null : !this$amount.equals(other$amount)) return false;
        final java.lang.Object this$reason = this.getReason();
        final java.lang.Object other$reason = other.getReason();
        if (this$reason == null ? other$reason != null : !this$reason.equals(other$reason)) return false;
        return true;
    }

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof PartialRefundRequest;
    }

    @java.lang.Override
    
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $amount = this.getAmount();
        result = result * PRIME + ($amount == null ? 43 : $amount.hashCode());
        final java.lang.Object $reason = this.getReason();
        result = result * PRIME + ($reason == null ? 43 : $reason.hashCode());
        return result;
    }

    @java.lang.Override
    
    public java.lang.String toString() {
        return "PartialRefundRequest(amount=" + this.getAmount() + ", reason=" + this.getReason() + ")";
    }
}
