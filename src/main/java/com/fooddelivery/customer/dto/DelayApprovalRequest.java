package com.fooddelivery.customer.dto;

public class DelayApprovalRequest {
    private boolean approved;

    @java.lang.SuppressWarnings("all")
    public DelayApprovalRequest() {
    }

    @java.lang.SuppressWarnings("all")
    public boolean isApproved() {
        return this.approved;
    }

    @java.lang.SuppressWarnings("all")
    public void setApproved(final boolean approved) {
        this.approved = approved;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof DelayApprovalRequest)) return false;
        final DelayApprovalRequest other = (DelayApprovalRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.isApproved() != other.isApproved()) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof DelayApprovalRequest;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isApproved() ? 79 : 97);
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "DelayApprovalRequest(approved=" + this.isApproved() + ")";
    }
}
