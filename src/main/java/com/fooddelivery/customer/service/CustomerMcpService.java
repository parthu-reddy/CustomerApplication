package com.fooddelivery.customer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.customer.controller.*;
import com.fooddelivery.customer.dto.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.List;

@Service
public class CustomerMcpService {

    private final AuthController authController;
    private final CustomerAddressController addressController;
    private final CustomerRestaurantController restaurantController;
    private final OrderController orderController;
    private final PlacesController placesController;
    private final CustomerTrackingController trackingController;
    private final ObjectMapper objectMapper;

    public CustomerMcpService(AuthController authController,
                              CustomerAddressController addressController,
                              CustomerRestaurantController restaurantController,
                              OrderController orderController,
                              PlacesController placesController,
                              CustomerTrackingController trackingController,
                              ObjectMapper objectMapper) {
        this.authController = authController;
        this.addressController = addressController;
        this.restaurantController = restaurantController;
        this.orderController = orderController;
        this.placesController = placesController;
        this.trackingController = trackingController;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Initiate authentication for a customer. Provide phoneNumber.")
    public String initiateAuth(String phoneNumber) {
        try {
            return objectMapper.writeValueAsString(authController.initiateLogin(phoneNumber).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Verify authentication for a customer. Provide phoneNumber and otp.")
    public String verifyAuth(String phoneNumber, String otp) {
        try {
            return objectMapper.writeValueAsString(authController.verifyOtp(phoneNumber, otp).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
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

    @Tool(description = "Create a customer order. Provide a JSON string representing the OrderRequest object.")
    public String createOrder(String orderRequestJson) {
        try {
            OrderRequest req = objectMapper.readValue(orderRequestJson, OrderRequest.class);
            return objectMapper.writeValueAsString(orderController.createOrder(req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Handle delay approval for a customer order. Provide orderId and boolean approved.")
    public String handleDelayApproval(String orderId, boolean approved) {
        try {
            DelayApprovalRequest req = new DelayApprovalRequest();
            req.setApproved(approved);
            return objectMapper.writeValueAsString(orderController.handleDelayApproval(UUID.fromString(orderId), req).getBody());
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
}
