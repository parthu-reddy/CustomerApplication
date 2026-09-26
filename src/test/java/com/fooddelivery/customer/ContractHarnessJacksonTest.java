package com.fooddelivery.customer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Holds the contract harness's serialization to the application's.
 *
 * <p>{@link ContractTestBase} drives standalone MockMvc, which does not receive Spring Boot's Jackson
 * auto-configuration. Left at its default it writes a {@code LocalDateTime} as an array of parts
 * ({@code [2026,9,11,10,15,30]}) while the running application writes an ISO-8601 string — so a
 * contract recorded through the harness would pin a shape no caller ever sees.
 * {@code contractMessageConverters()} corrects that by hand, and hand-derived configuration drifts;
 * this test is what stops it.
 *
 * <p>It is not a duplicate of the contract tests. Those assert a response against a contract recorded
 * through the harness, so a harness that serialises wrongly makes them agree with each other and with
 * nothing else. Only a comparison against Boot's own auto-configuration can catch that.
 *
 * <p>Uses {@link ApplicationContextRunner} rather than {@code @SpringBootTest}: a nested
 * {@code @SpringBootConfiguration} here becomes a second one in this package and breaks the
 * configuration search for every other test in it — which is exactly what happened on the first
 * attempt, failing {@code KafkaResilienceIntegrationTest}. The runner loads the real
 * {@link JacksonAutoConfiguration} with no such side effect.
 */
public class ContractHarnessJacksonTest {

    private final ApplicationContextRunner bootJackson = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(com.fooddelivery.common.config.JacksonConfig.class);

    private com.fasterxml.jackson.databind.ObjectMapper harnessMapper() {
        for (org.springframework.http.converter.HttpMessageConverter<?> c
                : ContractTestBase.contractMessageConverters()) {
            if (c instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter j) {
                return j.getObjectMapper();
            }
        }
        throw new IllegalStateException("the contract harness has no Jackson converter");
    }

    /** The case that actually bit: `deliveredAt` on the review-context contract. */
    @Test
    public void instantIsWrittenTheSameWayTheApplicationWritesIt() {
        java.time.Instant t = java.time.Instant.parse("2026-09-11T10:15:30Z");

        bootJackson.run(context -> {
            com.fasterxml.jackson.databind.ObjectMapper application =
                    context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
            assertEquals(application.writeValueAsString(t), harnessMapper().writeValueAsString(t),
                    "the contract harness no longer serialises dates the way the application does — "
                            + "every recorded contract carrying a date is now pinning a shape "
                            + "production does not send");
        });
    }

    /** Asserting the value too, so a regression in BOTH mappers at once still fails. */
    @Test
    public void andThatWayIsIso8601NotAnArrayOfParts() throws Exception {
        java.time.Instant t = java.time.Instant.parse("2026-09-11T10:15:30Z");

        assertEquals("\"2026-09-11T10:15:30Z\"", harnessMapper().writeValueAsString(t));
    }

    /** BigDecimal money fields: ten existing contracts assert them, none would have caught a change. */
    @Test
    public void bigDecimalIsWrittenTheSameWayTheApplicationWritesIt() {
        java.math.BigDecimal amount = new java.math.BigDecimal("25.00");

        bootJackson.run(context -> {
            com.fasterxml.jackson.databind.ObjectMapper application =
                    context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
            assertEquals(application.writeValueAsString(amount),
                    harnessMapper().writeValueAsString(amount));
        });
    }
}
