package com.fooddelivery.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("dev")
public class CoordinatePrinterTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void printCoordinates() {
        System.out.println("====== CUSTOMER HOME ADDRESS ======");
        List<Map<String, Object>> customers = jdbcTemplate.queryForList("SELECT c.phone, a.latitude, a.longitude FROM customer_addresses a JOIN customers c ON a.customer_id = c.id WHERE a.label = 'Home' AND c.phone = '8000000001'");
        for (Map<String, Object> c : customers) {
            System.out.println("Phone: " + c.get("phone") + ", Lat: " + c.get("latitude") + ", Lng: " + c.get("longitude"));
        }
        
        System.out.println("====== BRAND 1 OUTLETS ======");
        List<Map<String, Object>> outlets = jdbcTemplate.queryForList("SELECT r.name, r.latitude, r.longitude FROM restaurants r WHERE r.name LIKE 'Brand 1%'");
        for (Map<String, Object> o : outlets) {
            System.out.println("Outlet: " + o.get("name") + ", Lat: " + o.get("latitude") + ", Lng: " + o.get("longitude"));
        }
    }
}
