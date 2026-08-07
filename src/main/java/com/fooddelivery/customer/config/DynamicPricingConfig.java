package com.fooddelivery.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "pricing.delivery")
@RefreshScope
public class DynamicPricingConfig {
    private BigDecimal basePrice = new BigDecimal("15.00");
    private BigDecimal perKmRate = new BigDecimal("8.00");
    private BigDecimal restMaxContributionPercent = new BigDecimal("0.15");
    private BigDecimal fixedPlatformFee = new BigDecimal("5.00");
    private BigDecimal platformExcessCutPercent = new BigDecimal("0.50");
    private BigDecimal sgstPercent = new BigDecimal("0.025");
    private BigDecimal cgstPercent = new BigDecimal("0.025");

    @java.lang.SuppressWarnings("all")
    public DynamicPricingConfig() {
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getBasePrice() {
        return this.basePrice;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getPerKmRate() {
        return this.perKmRate;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getRestMaxContributionPercent() {
        return this.restMaxContributionPercent;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getFixedPlatformFee() {
        return this.fixedPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getPlatformExcessCutPercent() {
        return this.platformExcessCutPercent;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getSgstPercent() {
        return this.sgstPercent;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getCgstPercent() {
        return this.cgstPercent;
    }

    @java.lang.SuppressWarnings("all")
    public void setBasePrice(final BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    @java.lang.SuppressWarnings("all")
    public void setPerKmRate(final BigDecimal perKmRate) {
        this.perKmRate = perKmRate;
    }

    @java.lang.SuppressWarnings("all")
    public void setRestMaxContributionPercent(final BigDecimal restMaxContributionPercent) {
        this.restMaxContributionPercent = restMaxContributionPercent;
    }

    @java.lang.SuppressWarnings("all")
    public void setFixedPlatformFee(final BigDecimal fixedPlatformFee) {
        this.fixedPlatformFee = fixedPlatformFee;
    }

    @java.lang.SuppressWarnings("all")
    public void setPlatformExcessCutPercent(final BigDecimal platformExcessCutPercent) {
        this.platformExcessCutPercent = platformExcessCutPercent;
    }

    @java.lang.SuppressWarnings("all")
    public void setSgstPercent(final BigDecimal sgstPercent) {
        this.sgstPercent = sgstPercent;
    }

    @java.lang.SuppressWarnings("all")
    public void setCgstPercent(final BigDecimal cgstPercent) {
        this.cgstPercent = cgstPercent;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof DynamicPricingConfig)) return false;
        final DynamicPricingConfig other = (DynamicPricingConfig) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$basePrice = this.getBasePrice();
        final java.lang.Object other$basePrice = other.getBasePrice();
        if (this$basePrice == null ? other$basePrice != null : !this$basePrice.equals(other$basePrice)) return false;
        final java.lang.Object this$perKmRate = this.getPerKmRate();
        final java.lang.Object other$perKmRate = other.getPerKmRate();
        if (this$perKmRate == null ? other$perKmRate != null : !this$perKmRate.equals(other$perKmRate)) return false;
        final java.lang.Object this$restMaxContributionPercent = this.getRestMaxContributionPercent();
        final java.lang.Object other$restMaxContributionPercent = other.getRestMaxContributionPercent();
        if (this$restMaxContributionPercent == null ? other$restMaxContributionPercent != null : !this$restMaxContributionPercent.equals(other$restMaxContributionPercent)) return false;
        final java.lang.Object this$fixedPlatformFee = this.getFixedPlatformFee();
        final java.lang.Object other$fixedPlatformFee = other.getFixedPlatformFee();
        if (this$fixedPlatformFee == null ? other$fixedPlatformFee != null : !this$fixedPlatformFee.equals(other$fixedPlatformFee)) return false;
        final java.lang.Object this$platformExcessCutPercent = this.getPlatformExcessCutPercent();
        final java.lang.Object other$platformExcessCutPercent = other.getPlatformExcessCutPercent();
        if (this$platformExcessCutPercent == null ? other$platformExcessCutPercent != null : !this$platformExcessCutPercent.equals(other$platformExcessCutPercent)) return false;
        final java.lang.Object this$sgstPercent = this.getSgstPercent();
        final java.lang.Object other$sgstPercent = other.getSgstPercent();
        if (this$sgstPercent == null ? other$sgstPercent != null : !this$sgstPercent.equals(other$sgstPercent)) return false;
        final java.lang.Object this$cgstPercent = this.getCgstPercent();
        final java.lang.Object other$cgstPercent = other.getCgstPercent();
        if (this$cgstPercent == null ? other$cgstPercent != null : !this$cgstPercent.equals(other$cgstPercent)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof DynamicPricingConfig;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $basePrice = this.getBasePrice();
        result = result * PRIME + ($basePrice == null ? 43 : $basePrice.hashCode());
        final java.lang.Object $perKmRate = this.getPerKmRate();
        result = result * PRIME + ($perKmRate == null ? 43 : $perKmRate.hashCode());
        final java.lang.Object $restMaxContributionPercent = this.getRestMaxContributionPercent();
        result = result * PRIME + ($restMaxContributionPercent == null ? 43 : $restMaxContributionPercent.hashCode());
        final java.lang.Object $fixedPlatformFee = this.getFixedPlatformFee();
        result = result * PRIME + ($fixedPlatformFee == null ? 43 : $fixedPlatformFee.hashCode());
        final java.lang.Object $platformExcessCutPercent = this.getPlatformExcessCutPercent();
        result = result * PRIME + ($platformExcessCutPercent == null ? 43 : $platformExcessCutPercent.hashCode());
        final java.lang.Object $sgstPercent = this.getSgstPercent();
        result = result * PRIME + ($sgstPercent == null ? 43 : $sgstPercent.hashCode());
        final java.lang.Object $cgstPercent = this.getCgstPercent();
        result = result * PRIME + ($cgstPercent == null ? 43 : $cgstPercent.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "DynamicPricingConfig(basePrice=" + this.getBasePrice() + ", perKmRate=" + this.getPerKmRate() + ", restMaxContributionPercent=" + this.getRestMaxContributionPercent() + ", fixedPlatformFee=" + this.getFixedPlatformFee() + ", platformExcessCutPercent=" + this.getPlatformExcessCutPercent() + ", sgstPercent=" + this.getSgstPercent() + ", cgstPercent=" + this.getCgstPercent() + ")";
    }
}
