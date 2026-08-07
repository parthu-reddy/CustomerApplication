package com.fooddelivery.customer.model;

import com.fooddelivery.order.entity.OrderCharge;
import java.math.BigDecimal;
import java.util.Set;

public class PricingBreakdown {
    private BigDecimal totalCustomerDeliveryFee;
    private BigDecimal sgst;
    private BigDecimal cgst;
    private Set<OrderCharge> charges;

    @java.lang.SuppressWarnings("all")
    PricingBreakdown(final BigDecimal totalCustomerDeliveryFee, final BigDecimal sgst, final BigDecimal cgst, final Set<OrderCharge> charges) {
        this.totalCustomerDeliveryFee = totalCustomerDeliveryFee;
        this.sgst = sgst;
        this.cgst = cgst;
        this.charges = charges;
    }


    @java.lang.SuppressWarnings("all")
    public static class PricingBreakdownBuilder {
        @java.lang.SuppressWarnings("all")
        private BigDecimal totalCustomerDeliveryFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal sgst;
        @java.lang.SuppressWarnings("all")
        private BigDecimal cgst;
        @java.lang.SuppressWarnings("all")
        private Set<OrderCharge> charges;

        @java.lang.SuppressWarnings("all")
        PricingBreakdownBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder totalCustomerDeliveryFee(final BigDecimal totalCustomerDeliveryFee) {
            this.totalCustomerDeliveryFee = totalCustomerDeliveryFee;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder sgst(final BigDecimal sgst) {
            this.sgst = sgst;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder cgst(final BigDecimal cgst) {
            this.cgst = cgst;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder charges(final Set<OrderCharge> charges) {
            this.charges = charges;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown build() {
            return new PricingBreakdown(this.totalCustomerDeliveryFee, this.sgst, this.cgst, this.charges);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "PricingBreakdown.PricingBreakdownBuilder(totalCustomerDeliveryFee=" + this.totalCustomerDeliveryFee + ", sgst=" + this.sgst + ", cgst=" + this.cgst + ", charges=" + this.charges + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static PricingBreakdown.PricingBreakdownBuilder builder() {
        return new PricingBreakdown.PricingBreakdownBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getTotalCustomerDeliveryFee() {
        return this.totalCustomerDeliveryFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getSgst() {
        return this.sgst;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getCgst() {
        return this.cgst;
    }

    @java.lang.SuppressWarnings("all")
    public Set<OrderCharge> getCharges() {
        return this.charges;
    }

    @java.lang.SuppressWarnings("all")
    public void setTotalCustomerDeliveryFee(final BigDecimal totalCustomerDeliveryFee) {
        this.totalCustomerDeliveryFee = totalCustomerDeliveryFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setSgst(final BigDecimal sgst) {
        this.sgst = sgst;
    }

    @java.lang.SuppressWarnings("all")
    public void setCgst(final BigDecimal cgst) {
        this.cgst = cgst;
    }

    @java.lang.SuppressWarnings("all")
    public void setCharges(final Set<OrderCharge> charges) {
        this.charges = charges;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof PricingBreakdown)) return false;
        final PricingBreakdown other = (PricingBreakdown) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$totalCustomerDeliveryFee = this.getTotalCustomerDeliveryFee();
        final java.lang.Object other$totalCustomerDeliveryFee = other.getTotalCustomerDeliveryFee();
        if (this$totalCustomerDeliveryFee == null ? other$totalCustomerDeliveryFee != null : !this$totalCustomerDeliveryFee.equals(other$totalCustomerDeliveryFee)) return false;
        final java.lang.Object this$sgst = this.getSgst();
        final java.lang.Object other$sgst = other.getSgst();
        if (this$sgst == null ? other$sgst != null : !this$sgst.equals(other$sgst)) return false;
        final java.lang.Object this$cgst = this.getCgst();
        final java.lang.Object other$cgst = other.getCgst();
        if (this$cgst == null ? other$cgst != null : !this$cgst.equals(other$cgst)) return false;
        final java.lang.Object this$charges = this.getCharges();
        final java.lang.Object other$charges = other.getCharges();
        if (this$charges == null ? other$charges != null : !this$charges.equals(other$charges)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof PricingBreakdown;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $totalCustomerDeliveryFee = this.getTotalCustomerDeliveryFee();
        result = result * PRIME + ($totalCustomerDeliveryFee == null ? 43 : $totalCustomerDeliveryFee.hashCode());
        final java.lang.Object $sgst = this.getSgst();
        result = result * PRIME + ($sgst == null ? 43 : $sgst.hashCode());
        final java.lang.Object $cgst = this.getCgst();
        result = result * PRIME + ($cgst == null ? 43 : $cgst.hashCode());
        final java.lang.Object $charges = this.getCharges();
        result = result * PRIME + ($charges == null ? 43 : $charges.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "PricingBreakdown(totalCustomerDeliveryFee=" + this.getTotalCustomerDeliveryFee() + ", sgst=" + this.getSgst() + ", cgst=" + this.getCgst() + ", charges=" + this.getCharges() + ")";
    }
}
