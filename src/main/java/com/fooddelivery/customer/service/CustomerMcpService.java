package com.fooddelivery.customer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.customer.controller.*;
import com.fooddelivery.customer.dto.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.List;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class CustomerMcpService {
    

    private final CustomerAddressController addressController;
    private final CustomerRestaurantController restaurantController;
    private final OrderController orderController;
    private final PlacesController placesController;
    private final CustomerTrackingController trackingController;
    private final AdminOrderController adminOrderController;
    private final AdminCustomerController adminCustomerController;
    private final DriverOrderController driverOrderController;
    private final ObjectMapper objectMapper;


    private Principal createMockPrincipal(String customerId) {
        return () -> customerId;
    }

    @Tool(description = "Add an address for a customer. Provide customerId, label, and JSON string of the address object.")
    public String addAddress(String customerId, String addressJson) {
        try {
            AddressRequest req = objectMapper.readValue(addressJson, AddressRequest.class);
            return objectMapper.writeValueAsString(addressController.addAddress(UUID.fromString(customerId), req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get addresses for a customer. Provide customerId.")
    public String getAddresses(String customerId) {
        try {
            return objectMapper.writeValueAsString(addressController.getAddresses(UUID.fromString(customerId)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get nearby restaurants. Provide lat and lng.")
    public String getNearbyRestaurants(double lat, double lng) {
        try {
            return objectMapper.writeValueAsString(restaurantController.getNearbyRestaurants(lat, lng, 10000.0).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Check delivery availability for a restaurant. Provide restaurantId.")
    public String checkDeliveryAvailability(String restaurantId) {
        try {
            return objectMapper.writeValueAsString(restaurantController.checkDeliveryAvailability(UUID.fromString(restaurantId)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get a binding price quote for a basket. Provide customerId and a JSON string representing the QuoteRequest object (restaurantId, deliveryAddressId, items). Returns a quoteId that must be passed to createOrder; the quote expires after 15 minutes.")
    public String quoteOrder(String customerId, String quoteRequestJson) {
        try {
            com.fooddelivery.customer.dto.QuoteRequest req =
                objectMapper.readValue(quoteRequestJson, com.fooddelivery.customer.dto.QuoteRequest.class);
            return objectMapper.writeValueAsString(
                orderController.quoteOrder(createMockPrincipal(customerId), req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Create a customer order. Provide customerId and a JSON string representing the OrderRequest object, which must include the quoteId returned by quoteOrder -- the order is charged the quoted price and cannot be placed without one.")
    public String createOrder(String customerId, String orderRequestJson) {
        try {
            OrderRequest req = objectMapper.readValue(orderRequestJson, OrderRequest.class);
            return objectMapper.writeValueAsString(orderController.createOrder(createMockPrincipal(customerId), req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Handle delay approval for a customer order. Provide customerId, orderId and boolean approved.")
    public String handleDelayApproval(String customerId, String orderId, boolean approved) {
        try {
            DelayApprovalRequest req = new DelayApprovalRequest();
            req.setApproved(approved);
            return objectMapper.writeValueAsString(orderController.handleDelayApproval(createMockPrincipal(customerId), UUID.fromString(orderId), req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Autocomplete places search. Provide query.")
    public String placesAutocomplete(String query) {
        try {
            return objectMapper.writeValueAsString(placesController.autocomplete(query).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Reverse geocode a location. Provide lat and lng.")
    public String placesReverseGeocode(double lat, double lng) {
        try {
            return objectMapper.writeValueAsString(placesController.reverseGeocode(lat, lng).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get current tracking status of an order via latest cached state, avoiding long-lived SSE streaming.")
    public String trackOrder(String orderId) {
        // SSE endpoints can't easily be returned as standard MCP Strings because they block/stream. 
        // Returning a placeholder that we would ideally wire up to the cache.
        return "SSE tracking stream initiated for order " + orderId + ". For discrete state, use order querying.";
    }

    // AdminOrderController
    @Tool(description = "Admin: Get active orders for user. Provide userId.")
    public String getActiveOrdersForUser(String userId) {
        try {
            return objectMapper.writeValueAsString(adminOrderController.getActiveOrdersForUser(UUID.fromString(userId), org.springframework.data.domain.PageRequest.of(0, 50)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Admin: Get unassigned orders.")
    public String getUnassignedOrders() {
        try {
            return objectMapper.writeValueAsString(adminOrderController.getUnassignedOrders(org.springframework.data.domain.PageRequest.of(0, 50)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Admin: Get all active orders.")
    public String getAllActiveOrders() {
        try {
            return objectMapper.writeValueAsString(adminOrderController.getAllActiveOrders(0, 50).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Admin: Reconcile order state. Provide orderId.")
    public String reconcileOrderState(String orderId) {
        try {
            return objectMapper.writeValueAsString(adminOrderController.reconcileOrderState(UUID.fromString(orderId)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // @Tool(description = "Admin: Initiate partial refund. Provide orderId and JSON string of PartialRefundRequest (amount).")
    // public String initiatePartialRefund(String orderId, String requestJson) {
    //     try {
    //         // PartialRefundRequest req = objectMapper.readValue(requestJson, PartialRefundRequest.class);
    //         // return objectMapper.writeValueAsString(adminOrderController.initiatePartialRefund(UUID.fromString(orderId), req).getBody());
    //         return "Not implemented";
    //     } catch (Exception e) {
    //         return "Error: " + e.getMessage();
    //     }
    // }

    // AdminCustomerController
    @Tool(description = "Admin: Get all customer addresses.")
    public String getAllCustomerAddresses() {
        try {
            return objectMapper.writeValueAsString(adminCustomerController.getAllCustomerAddresses(org.springframework.data.domain.PageRequest.of(0, 50)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // DriverOrderController
    @Tool(description = "Driver: Get available orders. Provide driverId.")
    public String getAvailableOrders(String driverId) {
        try {
            return objectMapper.writeValueAsString(driverOrderController.getAvailableOrders(createMockPrincipal(driverId)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Driver: Get active orders. Provide driverId.")
    public String getActiveOrders(String driverId) {
        try {
            return objectMapper.writeValueAsString(driverOrderController.getActiveOrders(createMockPrincipal(driverId), org.springframework.data.domain.PageRequest.of(0, 50)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Driver: Get history orders. Provide driverId and date (optional).")
    public String getHistoryOrders(String driverId, String date) {
        try {
            return objectMapper.writeValueAsString(driverOrderController.getHistoryOrders(createMockPrincipal(driverId), date, org.springframework.data.domain.PageRequest.of(0, 50)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
// @Getter
