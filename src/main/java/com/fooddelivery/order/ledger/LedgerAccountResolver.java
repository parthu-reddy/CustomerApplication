package com.fooddelivery.order.ledger;

import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.enums.ChargeEntityType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LedgerAccountResolver {

    public ResolvedAccount resolve(ChargeEntityType entityType, UUID specificId, Order order) {
        if (entityType == null) {
            throw new IllegalArgumentException("ChargeEntityType cannot be null");
        }
        
        switch (entityType) {
            case PLATFORM:
                if (specificId != null && specificId.equals(LedgerAccounts.PLATFORM_REVENUE)) {
                    return new ResolvedAccount(LedgerAccountType.PLATFORM_REVENUE, LedgerAccounts.PLATFORM_REVENUE);
                }
                return new ResolvedAccount(LedgerAccountType.PLATFORM_CLEARING, LedgerAccounts.PLATFORM_CLEARING);
            
            case RESTAURANT:
                UUID restaurantId = (specificId != null) ? specificId : order.getRestaurantId();
                return new ResolvedAccount(LedgerAccountType.RESTAURANT_PAYABLE, restaurantId);
            
            case DRIVER:
                UUID driverId = (specificId != null) ? specificId : order.getDeliveryExecutiveId();
                if (driverId == null) {
                    throw new IllegalStateException("Order missing deliveryExecutiveId for DRIVER charge");
                }
                return new ResolvedAccount(LedgerAccountType.DRIVER_PAYABLE, driverId);
                
            case CUSTOMER:
                UUID customerId = (specificId != null) ? specificId : order.getCustomerId();
                return new ResolvedAccount(LedgerAccountType.CUSTOMER_CREDIT, customerId);
                
            case GOVERNMENT:
                return new ResolvedAccount(LedgerAccountType.TAX_PAYABLE, LedgerAccounts.TAX_PAYABLE);
                
            default:
                throw new IllegalArgumentException("Unknown ChargeEntityType: " + entityType);
        }
    }
}
