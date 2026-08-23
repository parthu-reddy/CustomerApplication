package com.fooddelivery.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AddressRequest {
    @NotBlank
    private String label;
    @NotBlank
    private String addressLine1;
    private String addressLine2;
    @NotBlank
    private String city;
    @NotBlank
    private String state;
    @NotBlank
    private String zipCode;
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;

    
    public AddressRequest() {
    }

    
    public String getLabel() {
        return this.label;
    }

    
    public String getAddressLine1() {
        return this.addressLine1;
    }

    
    public String getAddressLine2() {
        return this.addressLine2;
    }

    
    public String getCity() {
        return this.city;
    }

    
    public String getState() {
        return this.state;
    }

    
    public String getZipCode() {
        return this.zipCode;
    }

    
    public Double getLatitude() {
        return this.latitude;
    }

    
    public Double getLongitude() {
        return this.longitude;
    }

    
    public void setLabel(final String label) {
        this.label = label;
    }

    
    public void setAddressLine1(final String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    
    public void setAddressLine2(final String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    
    public void setCity(final String city) {
        this.city = city;
    }

    
    public void setState(final String state) {
        this.state = state;
    }

    
    public void setZipCode(final String zipCode) {
        this.zipCode = zipCode;
    }

    
    public void setLatitude(final Double latitude) {
        this.latitude = latitude;
    }

    
    public void setLongitude(final Double longitude) {
        this.longitude = longitude;
    }

    @java.lang.Override
    
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof AddressRequest)) return false;
        final AddressRequest other = (AddressRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$latitude = this.getLatitude();
        final java.lang.Object other$latitude = other.getLatitude();
        if (this$latitude == null ? other$latitude != null : !this$latitude.equals(other$latitude)) return false;
        final java.lang.Object this$longitude = this.getLongitude();
        final java.lang.Object other$longitude = other.getLongitude();
        if (this$longitude == null ? other$longitude != null : !this$longitude.equals(other$longitude)) return false;
        final java.lang.Object this$label = this.getLabel();
        final java.lang.Object other$label = other.getLabel();
        if (this$label == null ? other$label != null : !this$label.equals(other$label)) return false;
        final java.lang.Object this$addressLine1 = this.getAddressLine1();
        final java.lang.Object other$addressLine1 = other.getAddressLine1();
        if (this$addressLine1 == null ? other$addressLine1 != null : !this$addressLine1.equals(other$addressLine1)) return false;
        final java.lang.Object this$addressLine2 = this.getAddressLine2();
        final java.lang.Object other$addressLine2 = other.getAddressLine2();
        if (this$addressLine2 == null ? other$addressLine2 != null : !this$addressLine2.equals(other$addressLine2)) return false;
        final java.lang.Object this$city = this.getCity();
        final java.lang.Object other$city = other.getCity();
        if (this$city == null ? other$city != null : !this$city.equals(other$city)) return false;
        final java.lang.Object this$state = this.getState();
        final java.lang.Object other$state = other.getState();
        if (this$state == null ? other$state != null : !this$state.equals(other$state)) return false;
        final java.lang.Object this$zipCode = this.getZipCode();
        final java.lang.Object other$zipCode = other.getZipCode();
        if (this$zipCode == null ? other$zipCode != null : !this$zipCode.equals(other$zipCode)) return false;
        return true;
    }

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof AddressRequest;
    }

    @java.lang.Override
    
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $latitude = this.getLatitude();
        result = result * PRIME + ($latitude == null ? 43 : $latitude.hashCode());
        final java.lang.Object $longitude = this.getLongitude();
        result = result * PRIME + ($longitude == null ? 43 : $longitude.hashCode());
        final java.lang.Object $label = this.getLabel();
        result = result * PRIME + ($label == null ? 43 : $label.hashCode());
        final java.lang.Object $addressLine1 = this.getAddressLine1();
        result = result * PRIME + ($addressLine1 == null ? 43 : $addressLine1.hashCode());
        final java.lang.Object $addressLine2 = this.getAddressLine2();
        result = result * PRIME + ($addressLine2 == null ? 43 : $addressLine2.hashCode());
        final java.lang.Object $city = this.getCity();
        result = result * PRIME + ($city == null ? 43 : $city.hashCode());
        final java.lang.Object $state = this.getState();
        result = result * PRIME + ($state == null ? 43 : $state.hashCode());
        final java.lang.Object $zipCode = this.getZipCode();
        result = result * PRIME + ($zipCode == null ? 43 : $zipCode.hashCode());
        return result;
    }

    @java.lang.Override
    
    public java.lang.String toString() {
        return "AddressRequest(label=" + this.getLabel() + ", addressLine1=" + this.getAddressLine1() + ", addressLine2=" + this.getAddressLine2() + ", city=" + this.getCity() + ", state=" + this.getState() + ", zipCode=" + this.getZipCode() + ", latitude=" + this.getLatitude() + ", longitude=" + this.getLongitude() + ")";
    }
}
