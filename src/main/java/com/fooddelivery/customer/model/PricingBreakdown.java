package com.fooddelivery.customer.model;

import com.fooddelivery.order.entity.OrderCharge;
import java.math.BigDecimal;
import java.util.Set;

public class PricingBreakdown {
    private BigDecimal totalCustomerDeliveryFee;
    private BigDecimal sgst;
    private BigDecimal cgst;

    private BigDecimal itemTotal;
    private BigDecimal customerPlatformFee;
    private BigDecimal restaurantPlatformFee;
    private BigDecimal platformBonus;
    private BigDecimal restaurantDeliveryContribution;
    private BigDecimal restaurantPayout;
    private BigDecimal deliveryFee;
    private BigDecimal driverGrossPayout;
    private BigDecimal driverTaxes;
    private BigDecimal driverNetPayout;

    private Set<OrderCharge> charges;

    @java.lang.SuppressWarnings("all")
    PricingBreakdown(final BigDecimal totalCustomerDeliveryFee, final BigDecimal sgst, final BigDecimal cgst, final BigDecimal itemTotal, final BigDecimal customerPlatformFee, final BigDecimal restaurantPlatformFee, final BigDecimal platformBonus, final BigDecimal restaurantDeliveryContribution, final BigDecimal restaurantPayout, final BigDecimal deliveryFee, final BigDecimal driverGrossPayout, final BigDecimal driverTaxes, final BigDecimal driverNetPayout, final Set<OrderCharge> charges) {
        this.totalCustomerDeliveryFee = totalCustomerDeliveryFee;
        this.sgst = sgst;
        this.cgst = cgst;
        this.itemTotal = itemTotal;
        this.customerPlatformFee = customerPlatformFee;
        this.restaurantPlatformFee = restaurantPlatformFee;
        this.platformBonus = platformBonus;
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
        this.restaurantPayout = restaurantPayout;
        this.deliveryFee = deliveryFee;
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

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
        private BigDecimal itemTotal;
        @java.lang.SuppressWarnings("all")
        private BigDecimal customerPlatformFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal restaurantPlatformFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal platformBonus;
        @java.lang.SuppressWarnings("all")
        private BigDecimal restaurantDeliveryContribution;
        @java.lang.SuppressWarnings("all")
        private BigDecimal restaurantPayout;
        @java.lang.SuppressWarnings("all")
        private BigDecimal deliveryFee;
        @java.lang.SuppressWarnings("all")
        private BigDecimal driverGrossPayout;
        @java.lang.SuppressWarnings("all")
        private BigDecimal driverTaxes;
        @java.lang.SuppressWarnings("all")
        private BigDecimal driverNetPayout;

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
public PricingBreakdown.PricingBreakdownBuilder itemTotal(final BigDecimal itemTotal) {
            this.itemTotal = itemTotal;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder customerPlatformFee(final BigDecimal customerPlatformFee) {
            this.customerPlatformFee = customerPlatformFee;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder restaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
            this.restaurantPlatformFee = restaurantPlatformFee;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder platformBonus(final BigDecimal platformBonus) {
            this.platformBonus = platformBonus;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder restaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
            this.restaurantDeliveryContribution = restaurantDeliveryContribution;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder restaurantPayout(final BigDecimal restaurantPayout) {
            this.restaurantPayout = restaurantPayout;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder deliveryFee(final BigDecimal deliveryFee) {
            this.deliveryFee = deliveryFee;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder driverGrossPayout(final BigDecimal driverGrossPayout) {
            this.driverGrossPayout = driverGrossPayout;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder driverTaxes(final BigDecimal driverTaxes) {
            this.driverTaxes = driverTaxes;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public PricingBreakdown.PricingBreakdownBuilder driverNetPayout(final BigDecimal driverNetPayout) {
            this.driverNetPayout = driverNetPayout;
            return this;
        }

        public PricingBreakdown.PricingBreakdownBuilder cgst(final BigDecimal cgst) {
            this.cgst = cgst;
        this.itemTotal = itemTotal;
        this.customerPlatformFee = customerPlatformFee;
        this.restaurantPlatformFee = restaurantPlatformFee;
        this.platformBonus = platformBonus;
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
        this.restaurantPayout = restaurantPayout;
        this.deliveryFee = deliveryFee;
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

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
            return new PricingBreakdown(this.totalCustomerDeliveryFee, this.sgst, this.cgst, this.itemTotal, this.customerPlatformFee, this.restaurantPlatformFee, this.platformBonus, this.restaurantDeliveryContribution, this.restaurantPayout, this.deliveryFee, this.driverGrossPayout, this.driverTaxes, this.driverNetPayout, this.charges);
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
public BigDecimal getItemTotal() {
        return this.itemTotal;
    }

    @java.lang.SuppressWarnings("all")
    public void setItemTotal(final BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getCustomerPlatformFee() {
        return this.customerPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerPlatformFee(final BigDecimal customerPlatformFee) {
        this.customerPlatformFee = customerPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestaurantPlatformFee() {
        return this.restaurantPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
        this.restaurantPlatformFee = restaurantPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getPlatformBonus() {
        return this.platformBonus;
    }

    @java.lang.SuppressWarnings("all")
    public void setPlatformBonus(final BigDecimal platformBonus) {
        this.platformBonus = platformBonus;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestaurantDeliveryContribution() {
        return this.restaurantDeliveryContribution;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestaurantPayout() {
        return this.restaurantPayout;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestaurantPayout(final BigDecimal restaurantPayout) {
        this.restaurantPayout = restaurantPayout;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDeliveryFee() {
        return this.deliveryFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeliveryFee(final BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDriverGrossPayout() {
        return this.driverGrossPayout;
    }

    @java.lang.SuppressWarnings("all")
    public void setDriverGrossPayout(final BigDecimal driverGrossPayout) {
        this.driverGrossPayout = driverGrossPayout;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDriverTaxes() {
        return this.driverTaxes;
    }

    @java.lang.SuppressWarnings("all")
    public void setDriverTaxes(final BigDecimal driverTaxes) {
        this.driverTaxes = driverTaxes;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getDriverNetPayout() {
        return this.driverNetPayout;
    }

    @java.lang.SuppressWarnings("all")
    public void setDriverNetPayout(final BigDecimal driverNetPayout) {
        this.driverNetPayout = driverNetPayout;
    }

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
        this.itemTotal = itemTotal;
        this.customerPlatformFee = customerPlatformFee;
        this.restaurantPlatformFee = restaurantPlatformFee;
        this.platformBonus = platformBonus;
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
        this.restaurantPayout = restaurantPayout;
        this.deliveryFee = deliveryFee;
        this.driverGrossPayout = driverGrossPayout;
        this.driverTaxes = driverTaxes;
        this.driverNetPayout = driverNetPayout;

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
