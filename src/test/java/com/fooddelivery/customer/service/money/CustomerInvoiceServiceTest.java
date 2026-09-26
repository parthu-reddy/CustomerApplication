package com.fooddelivery.customer.service.money;

import com.fooddelivery.common.enums.DeliveryStatus;
import com.fooddelivery.customer.client.RestaurantClient;
import com.fooddelivery.customer.config.InvoiceOperatorConfig;
import com.fooddelivery.customer.dto.CustomerInvoice;
import com.fooddelivery.order.entity.Order;
import com.fooddelivery.order.entity.OrderInvoice;
import com.fooddelivery.order.entity.OrderItem;
import com.fooddelivery.order.repository.IOrderRepository;
import com.fooddelivery.order.repository.OrderInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerInvoiceServiceTest {

    private final IOrderRepository orders = mock(IOrderRepository.class);
    private final OrderInvoiceRepository invoices = mock(OrderInvoiceRepository.class);
    private final InvoiceNumberAllocator allocator = mock(InvoiceNumberAllocator.class);
    private final RestaurantClient restaurants = mock(RestaurantClient.class);
    private final InvoiceOperatorConfig operator = new InvoiceOperatorConfig();
    private final CustomerInvoiceService service = new CustomerInvoiceService(orders, invoices, allocator, restaurants, operator);

    private final UUID customerId = UUID.randomUUID();
    private Order order;
    private final AtomicReference<OrderInvoice> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setRestaurantId(UUID.randomUUID());
        order.setRestaurantName("Paradise");
        order.setCustomerName("Asha");
        order.setDeliveryAddress("12 MG Road, Bengaluru");
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        // 20:15 and 19:30 in Bengaluru, where the supplier trades.
        order.setDeliveredAt(java.time.Instant.parse("2026-09-24T14:45:00Z"));
        order.setCreatedAt(java.time.Instant.parse("2026-09-24T14:00:00Z"));
        OrderItem biryani = new OrderItem();
        biryani.setName("Chicken Biryani");
        biryani.setQuantity(2);
        biryani.setPrice(new BigDecimal("290.00"));
        order.setOrderItems(new java.util.HashSet<>(Set.of(biryani)));
        order.setCgst(new BigDecimal("14.50"));
        order.setSgst(new BigDecimal("14.50"));
        order.setDeliveryFee(new BigDecimal("30.00"));
        order.setCustomerPlatformFee(new BigDecimal("5.00"));
        order.setTotalAmount(new BigDecimal("644.00"));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(invoices.findById(order.getId())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(invoices.saveAndFlush(any())).thenAnswer(i -> { stored.set(i.getArgument(0)); return i.getArgument(0); });
        Map<String, Object> supplier = new HashMap<>();
        supplier.put("legalEntityName", "Paradise Food Court Pvt Ltd");
        supplier.put("outletName", "Paradise Koramangala");
        supplier.put("gstin", "29ABCDE1234F1Z5");
        supplier.put("fssaiLicenseNumber", "11224333000123");
        supplier.put("timeZone", "Asia/Kolkata");
        when(restaurants.getInvoiceDetails(order.getRestaurantId())).thenReturn(supplier);
        when(allocator.next(any())).thenReturn("FD/2627/0000001", "FD/2627/0000002");
    }

    @Test
    void issuesOnce_andAddsUpToWhatWasCharged() {
        CustomerInvoice first = service.getInvoice(order.getId(), customerId);
        CustomerInvoice again = service.getInvoice(order.getId(), customerId);

        assertEquals("FD/2627/0000001", first.getInvoiceNumber());
        assertEquals(first.getInvoiceNumber(), again.getInvoiceNumber());
        verify(allocator, times(1)).next(LocalDate.of(2026, 9, 24));
        assertEquals("Chicken Biryani", first.getLines().get(0).getDescription());
        assertEquals("996331", first.getLines().get(0).getSac());
        assertEquals(0, new BigDecimal("580.00").compareTo(first.getTaxableValue()));
        assertEquals(0, new BigDecimal("2.5").compareTo(first.getCgstRatePercent()));
        BigDecimal sum = first.getTaxableValue().add(first.getCgstAmount()).add(first.getSgstAmount())
                .add(first.getDeliveryFee()).add(first.getPlatformFee());
        assertEquals(0, first.getTotal().compareTo(sum), "the invoice lines must add up to the total charged");
        assertEquals("29ABCDE1234F1Z5", first.getSupplier().getGstin());
        assertNull(first.getOperator(), "no operator block until its legal details are configured");
    }

    @Test
    void theSupplierIsASnapshot_laterEditsDoNotChangeAnIssuedInvoice() {
        service.getInvoice(order.getId(), customerId);
        when(restaurants.getInvoiceDetails(any())).thenReturn(Map.of("legalEntityName", "Renamed Ltd"));
        assertEquals("Paradise Food Court Pvt Ltd", service.getInvoice(order.getId(), customerId).getSupplier().getLegalName());
    }

    @Test
    void notBeforeDelivery() {
        order.setDeliveredAt(null);
        order.setDeliveryStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getInvoice(order.getId(), customerId));
        assertEquals(409, e.getStatusCode().value());
        verifyNoInteractions(allocator);
    }

    @Test
    void onlyForTheOrdersCustomer() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getInvoice(order.getId(), UUID.randomUUID()));
        assertEquals(403, e.getStatusCode().value());
    }

    @Test
    void noNumberIsSpentWithoutTheSupplier() {
        when(restaurants.getInvoiceDetails(any())).thenThrow(new IllegalStateException("down"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getInvoice(order.getId(), customerId));
        assertEquals(503, e.getStatusCode().value());
        verifyNoInteractions(allocator);
    }

    /**
     * The invoice date, and so its financial year, is read on the supplier's calendar. 2027-03-31T20:00Z
     * is already 1 April in Kolkata (a new Indian financial year) but still 31 March in New York.
     * It used to be the UTC date. TimezoneCorrectness_2026-09-25.
     */
    @Test
    void theFinancialYearFollowsTheSuppliersCalendar() {
        java.time.Instant lateOn31March = java.time.Instant.parse("2027-03-31T20:00:00Z");
        LocalDate kolkata = CustomerInvoiceService.issuedOn(lateOn31March, Map.of("timeZone", "Asia/Kolkata"));
        LocalDate newYork = CustomerInvoiceService.issuedOn(lateOn31March, Map.of("timeZone", "America/New_York"));

        assertEquals(LocalDate.of(2027, 4, 1), kolkata);
        assertEquals(LocalDate.of(2027, 3, 31), newYork);
        assertEquals("FD/2728/0000001", InvoiceNumberAllocator.format(1, kolkata));
        assertEquals("FD/2627/0000001", InvoiceNumberAllocator.format(1, newYork));
    }

    /** A tax document never guesses its zone: no zone from the supplier, no number spent. */
    @Test
    void noSupplierZoneMeansNoInvoiceYet() {
        Map<String, Object> zoneless = new HashMap<>(Map.of("legalEntityName", "Paradise Food Court Pvt Ltd"));
        when(restaurants.getInvoiceDetails(any())).thenReturn(zoneless);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getInvoice(order.getId(), customerId));
        assertEquals(503, e.getStatusCode().value());
        verifyNoInteractions(allocator);
    }

    @Test
    void operatorBlockAppearsOnceConfigured() {
        operator.setLegalName("FoodDelivery Technologies Pvt Ltd");
        operator.setGstin("29AAAAA0000A1Z5");
        assertEquals("29AAAAA0000A1Z5", service.getInvoice(order.getId(), customerId).getOperator().getGstin());
    }

    @Test
    void numbersCarryTheIndianFinancialYear_withinSixteenCharacters() {
        assertEquals("FD/2627/0000123", InvoiceNumberAllocator.format(123, LocalDate.of(2026, 9, 24)));
        assertEquals("FD/2526/0000123", InvoiceNumberAllocator.format(123, LocalDate.of(2026, 3, 31)));
        assertEquals("FD/2627/0000001", InvoiceNumberAllocator.format(1, LocalDate.of(2026, 4, 1)));
        assertTrue(InvoiceNumberAllocator.format(99_999_999, LocalDate.of(2026, 9, 24)).length() <= 16);
    }
}
