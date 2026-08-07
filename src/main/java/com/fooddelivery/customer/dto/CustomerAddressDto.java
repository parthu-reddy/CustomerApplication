package com.fooddelivery.customer.dto;

import java.util.UUID;

public class CustomerAddressDto {
    private UUID id;
    private UUID customerId;
    private String label;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;

    @java.lang.SuppressWarnings("all")
    CustomerAddressDto(final UUID id, final UUID customerId, final String label, final String addressLine1, final String addressLine2, final String city, final String state, final String zipCode, final Double latitude, final Double longitude, final Boolean isDefault) {
        this.id = id;
        this.customerId = customerId;
        this.label = label;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isDefault = isDefault;
    }


    @java.lang.SuppressWarnings("all")
    public static class CustomerAddressDtoBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private UUID customerId;
        @java.lang.SuppressWarnings("all")
        private String label;
        @java.lang.SuppressWarnings("all")
        private String addressLine1;
        @java.lang.SuppressWarnings("all")
        private String addressLine2;
        @java.lang.SuppressWarnings("all")
        private String city;
        @java.lang.SuppressWarnings("all")
        private String state;
        @java.lang.SuppressWarnings("all")
        private String zipCode;
        @java.lang.SuppressWarnings("all")
        private Double latitude;
        @java.lang.SuppressWarnings("all")
        private Double longitude;
        @java.lang.SuppressWarnings("all")
        private Boolean isDefault;

        @java.lang.SuppressWarnings("all")
        CustomerAddressDtoBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder customerId(final UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder label(final String label) {
            this.label = label;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder addressLine1(final String addressLine1) {
            this.addressLine1 = addressLine1;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder addressLine2(final String addressLine2) {
            this.addressLine2 = addressLine2;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder city(final String city) {
            this.city = city;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder state(final String state) {
            this.state = state;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder zipCode(final String zipCode) {
            this.zipCode = zipCode;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder latitude(final Double latitude) {
            this.latitude = latitude;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder longitude(final Double longitude) {
            this.longitude = longitude;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto.CustomerAddressDtoBuilder isDefault(final Boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public CustomerAddressDto build() {
            return new CustomerAddressDto(this.id, this.customerId, this.label, this.addressLine1, this.addressLine2, this.city, this.state, this.zipCode, this.latitude, this.longitude, this.isDefault);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "CustomerAddressDto.CustomerAddressDtoBuilder(id=" + this.id + ", customerId=" + this.customerId + ", label=" + this.label + ", addressLine1=" + this.addressLine1 + ", addressLine2=" + this.addressLine2 + ", city=" + this.city + ", state=" + this.state + ", zipCode=" + this.zipCode + ", latitude=" + this.latitude + ", longitude=" + this.longitude + ", isDefault=" + this.isDefault + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static CustomerAddressDto.CustomerAddressDtoBuilder builder() {
        return new CustomerAddressDto.CustomerAddressDtoBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getCustomerId() {
        return this.customerId;
    }

    @java.lang.SuppressWarnings("all")
    public String getLabel() {
        return this.label;
    }

    @java.lang.SuppressWarnings("all")
    public String getAddressLine1() {
        return this.addressLine1;
    }

    @java.lang.SuppressWarnings("all")
    public String getAddressLine2() {
        return this.addressLine2;
    }

    @java.lang.SuppressWarnings("all")
    public String getCity() {
        return this.city;
    }

    @java.lang.SuppressWarnings("all")
    public String getState() {
        return this.state;
    }

    @java.lang.SuppressWarnings("all")
    public String getZipCode() {
        return this.zipCode;
    }

    @java.lang.SuppressWarnings("all")
    public Double getLatitude() {
        return this.latitude;
    }

    @java.lang.SuppressWarnings("all")
    public Double getLongitude() {
        return this.longitude;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsDefault() {
        return this.isDefault;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setCustomerId(final UUID customerId) {
        this.customerId = customerId;
    }

    @java.lang.SuppressWarnings("all")
    public void setLabel(final String label) {
        this.label = label;
    }

    @java.lang.SuppressWarnings("all")
    public void setAddressLine1(final String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    @java.lang.SuppressWarnings("all")
    public void setAddressLine2(final String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    @java.lang.SuppressWarnings("all")
    public void setCity(final String city) {
        this.city = city;
    }

    @java.lang.SuppressWarnings("all")
    public void setState(final String state) {
        this.state = state;
    }

    @java.lang.SuppressWarnings("all")
    public void setZipCode(final String zipCode) {
        this.zipCode = zipCode;
    }

    @java.lang.SuppressWarnings("all")
    public void setLatitude(final Double latitude) {
        this.latitude = latitude;
    }

    @java.lang.SuppressWarnings("all")
    public void setLongitude(final Double longitude) {
        this.longitude = longitude;
    }

    @java.lang.SuppressWarnings("all")
    public void setIsDefault(final Boolean isDefault) {
        this.isDefault = isDefault;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof CustomerAddressDto)) return false;
        final CustomerAddressDto other = (CustomerAddressDto) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$latitude = this.getLatitude();
        final java.lang.Object other$latitude = other.getLatitude();
        if (this$latitude == null ? other$latitude != null : !this$latitude.equals(other$latitude)) return false;
        final java.lang.Object this$longitude = this.getLongitude();
        final java.lang.Object other$longitude = other.getLongitude();
        if (this$longitude == null ? other$longitude != null : !this$longitude.equals(other$longitude)) return false;
        final java.lang.Object this$isDefault = this.getIsDefault();
        final java.lang.Object other$isDefault = other.getIsDefault();
        if (this$isDefault == null ? other$isDefault != null : !this$isDefault.equals(other$isDefault)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$customerId = this.getCustomerId();
        final java.lang.Object other$customerId = other.getCustomerId();
        if (this$customerId == null ? other$customerId != null : !this$customerId.equals(other$customerId)) return false;
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

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof CustomerAddressDto;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $latitude = this.getLatitude();
        result = result * PRIME + ($latitude == null ? 43 : $latitude.hashCode());
        final java.lang.Object $longitude = this.getLongitude();
        result = result * PRIME + ($longitude == null ? 43 : $longitude.hashCode());
        final java.lang.Object $isDefault = this.getIsDefault();
        result = result * PRIME + ($isDefault == null ? 43 : $isDefault.hashCode());
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $customerId = this.getCustomerId();
        result = result * PRIME + ($customerId == null ? 43 : $customerId.hashCode());
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
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "CustomerAddressDto(id=" + this.getId() + ", customerId=" + this.getCustomerId() + ", label=" + this.getLabel() + ", addressLine1=" + this.getAddressLine1() + ", addressLine2=" + this.getAddressLine2() + ", city=" + this.getCity() + ", state=" + this.getState() + ", zipCode=" + this.getZipCode() + ", latitude=" + this.getLatitude() + ", longitude=" + this.getLongitude() + ", isDefault=" + this.getIsDefault() + ")";
    }
}
