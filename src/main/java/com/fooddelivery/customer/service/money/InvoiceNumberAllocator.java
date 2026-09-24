package com.fooddelivery.customer.service.money;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Invoice numbers: {@code FD/2627/0000123} -- a prefix, the Indian financial year (April to
 * March) and a serial from the {@code order_invoice_seq} database sequence.
 *
 * <p>GST rule 46 asks for a consecutive serial, unique within the financial year, of at most 16
 * characters. The sequence is global, so numbers are unique outright; with the year in the number
 * they are unique per year too. This format is 15 characters up to 9,999,999 invoices and 16 up
 * to 99,999,999. A sequence can skip a value if a transaction rolls back after taking one; GST
 * practice treats that as a gap to explain, not a duplicate.
 */
@Component
@lombok.RequiredArgsConstructor
public class InvoiceNumberAllocator {

    private final JdbcTemplate jdbcTemplate;

    public String next(LocalDate issuedOn) {
        Long serial = jdbcTemplate.queryForObject("SELECT nextval('order_invoice_seq')", Long.class);
        if (serial == null) throw new IllegalStateException("order_invoice_seq returned no value");
        return format(serial, issuedOn);
    }

    static String format(long serial, LocalDate issuedOn) {
        int startYear = issuedOn.getMonthValue() >= 4 ? issuedOn.getYear() : issuedOn.getYear() - 1;
        String fy = String.format("%02d%02d", startYear % 100, (startYear + 1) % 100);
        return String.format("FD/%s/%07d", fy, serial);
    }
}
