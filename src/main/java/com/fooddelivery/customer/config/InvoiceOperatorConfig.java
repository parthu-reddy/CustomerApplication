package com.fooddelivery.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The platform's own details for the tax invoice. Under GST section 9(5) the e-commerce operator
 * pays the GST on restaurant services sold through it, so its legal name and GSTIN belong on the
 * invoice beside the restaurant's.
 *
 * <p>No defaults on purpose: these are legal facts only the business can supply. Until they are
 * set the invoice leaves the block out rather than printing a placeholder
 * ({@code invoice.operator.legal-name}, {@code .gstin}, {@code .address}).
 */
@Configuration
@ConfigurationProperties(prefix = "invoice.operator")
@lombok.Data
public class InvoiceOperatorConfig {
    private String legalName;
    private String gstin;
    private String address;
}
