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


    
    public static class PricingBreakdownBuilder {
        
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

        
        PricingBreakdownBuilder() {
        }

        /**
         * @return {@code this}.
         */
        
        public PricingBreakdown.PricingBreakdownBuilder totalCustomerDeliveryFee(final BigDecimal totalCustomerDeliveryFee) {
            this.totalCustomerDeliveryFee = totalCustomerDeliveryFee;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
        public PricingBreakdown.PricingBreakdownBuilder sgst(final BigDecimal sgst) {
            this.sgst = sgst;
            return this;
        }

        /**
         * @return {@code this}.
         */
        
public PricingBreakdown.PricingBreakdownBuilder itemTotal(final BigDecimal itemTotal) {
            this.itemTotal = itemTotal;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder customerPlatformFee(final BigDecimal customerPlatformFee) {
            this.customerPlatformFee = customerPlatformFee;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder restaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
            this.restaurantPlatformFee = restaurantPlatformFee;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder platformBonus(final BigDecimal platformBonus) {
            this.platformBonus = platformBonus;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder restaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
            this.restaurantDeliveryContribution = restaurantDeliveryContribution;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder restaurantPayout(final BigDecimal restaurantPayout) {
            this.restaurantPayout = restaurantPayout;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder deliveryFee(final BigDecimal deliveryFee) {
            this.deliveryFee = deliveryFee;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder driverGrossPayout(final BigDecimal driverGrossPayout) {
            this.driverGrossPayout = driverGrossPayout;
            return this;
        }

        
        public PricingBreakdown.PricingBreakdownBuilder driverTaxes(final BigDecimal driverTaxes) {
            this.driverTaxes = driverTaxes;
            return this;
        }

        
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
        
        public PricingBreakdown.PricingBreakdownBuilder charges(final Set<OrderCharge> charges) {
            this.charges = charges;
            return this;
        }

        
        public PricingBreakdown build() {
            return new PricingBreakdown(this.totalCustomerDeliveryFee, this.sgst, this.cgst, this.itemTotal, this.customerPlatformFee, this.restaurantPlatformFee, this.platformBonus, this.restaurantDeliveryContribution, this.restaurantPayout, this.deliveryFee, this.driverGrossPayout, this.driverTaxes, this.driverNetPayout, this.charges);
        }

        @java.lang.Override
        
        public java.lang.String toString() {
            return "PricingBreakdown.PricingBreakdownBuilder(totalCustomerDeliveryFee=" + this.totalCustomerDeliveryFee + ", sgst=" + this.sgst + ", cgst=" + this.cgst + ", charges=" + this.charges + ")";
        }
    }

    
    public static PricingBreakdown.PricingBreakdownBuilder builder() {
        return new PricingBreakdown.PricingBreakdownBuilder();
    }

    
    public BigDecimal getTotalCustomerDeliveryFee() {
        return this.totalCustomerDeliveryFee;
    }

    
    public BigDecimal getSgst() {
        return this.sgst;
    }

    
public BigDecimal getItemTotal() {
        return this.itemTotal;
    }

    
    public void setItemTotal(final BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    
    public BigDecimal getCustomerPlatformFee() {
        return this.customerPlatformFee;
    }

    
    public void setCustomerPlatformFee(final BigDecimal customerPlatformFee) {
        this.customerPlatformFee = customerPlatformFee;
    }

    
    public BigDecimal getRestaurantPlatformFee() {
        return this.restaurantPlatformFee;
    }

    
    public void setRestaurantPlatformFee(final BigDecimal restaurantPlatformFee) {
        this.restaurantPlatformFee = restaurantPlatformFee;
    }

    
    public BigDecimal getPlatformBonus() {
        return this.platformBonus;
    }

    
    public void setPlatformBonus(final BigDecimal platformBonus) {
        this.platformBonus = platformBonus;
    }

    
    public BigDecimal getRestaurantDeliveryContribution() {
        return this.restaurantDeliveryContribution;
    }

    
    public void setRestaurantDeliveryContribution(final BigDecimal restaurantDeliveryContribution) {
        this.restaurantDeliveryContribution = restaurantDeliveryContribution;
    }

    
    public BigDecimal getRestaurantPayout() {
        return this.restaurantPayout;
    }

    
    public void setRestaurantPayout(final BigDecimal restaurantPayout) {
        this.restaurantPayout = restaurantPayout;
    }

    
    public BigDecimal getDeliveryFee() {
        return this.deliveryFee;
    }

    
    public void setDeliveryFee(final BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    
    public BigDecimal getDriverGrossPayout() {
        return this.driverGrossPayout;
    }

    
    public void setDriverGrossPayout(final BigDecimal driverGrossPayout) {
        this.driverGrossPayout = driverGrossPayout;
    }

    
    public BigDecimal getDriverTaxes() {
        return this.driverTaxes;
    }

    
    public void setDriverTaxes(final BigDecimal driverTaxes) {
        this.driverTaxes = driverTaxes;
    }

    
    public BigDecimal getDriverNetPayout() {
        return this.driverNetPayout;
    }

    
    public void setDriverNetPayout(final BigDecimal driverNetPayout) {
        this.driverNetPayout = driverNetPayout;
    }

    public BigDecimal getCgst() {
        return this.cgst;
    }

    
    public Set<OrderCharge> getCharges() {
        return this.charges;
    }

    
    public void setTotalCustomerDeliveryFee(final BigDecimal totalCustomerDeliveryFee) {
        this.totalCustomerDeliveryFee = totalCustomerDeliveryFee;
    }

    
    public void setSgst(final BigDecimal sgst) {
        this.sgst = sgst;
    }

    
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

    
    public void setCharges(final Set<OrderCharge> charges) {
        this.charges = charges;
    }

    @java.lang.Override
    
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

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof PricingBreakdown;
    }

    @java.lang.Override
    
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
    
    public java.lang.String toString() {
        return "PricingBreakdown(totalCustomerDeliveryFee=" + this.getTotalCustomerDeliveryFee() + ", sgst=" + this.getSgst() + ", cgst=" + this.getCgst() + ", charges=" + this.getCharges() + ")";
    }
}
