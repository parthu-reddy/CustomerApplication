package com.fooddelivery.customer.dto;

public class DelayApprovalRequest {
    private boolean approved;

    
    public DelayApprovalRequest() {
    }

    
    public boolean isApproved() {
        return this.approved;
    }

    
    public void setApproved(final boolean approved) {
        this.approved = approved;
    }

    @java.lang.Override
    
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof DelayApprovalRequest)) return false;
        final DelayApprovalRequest other = (DelayApprovalRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.isApproved() != other.isApproved()) return false;
        return true;
    }

    
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof DelayApprovalRequest;
    }

    @java.lang.Override
    
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isApproved() ? 79 : 97);
        return result;
    }

    @java.lang.Override
    
    public java.lang.String toString() {
        return "DelayApprovalRequest(approved=" + this.isApproved() + ")";
    }
}
