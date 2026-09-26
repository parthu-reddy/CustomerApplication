package com.fooddelivery.customer.service.money;

import com.fooddelivery.common.time.BusinessCalendar;
import com.fooddelivery.common.time.TimeWindow;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The calendar period an outlet's earnings summary covers: the current day, week or month on the
 * outlet's own calendar ({@code outlets.time_zone}), as a half-open {@code [from, to)} window.
 *
 * <p>A day is 23, 24 or 25 hours, a week 167 to 169, and a month whatever its days add up to in
 * that zone. Weeks start on Monday (ISO-8601): outlets carry a zone but no locale, so there is
 * nothing to read a different first day from.
 *
 * <p>This replaced a fixed "one UTC month back from now" that ignored the period it was asked for.
 * TimezoneCorrectness_2026-09-25.
 */
public enum SummaryPeriod {

    TODAY("today"),
    WEEK("week"),
    MONTH("month");

    /** Every accepted value, for the message a caller sees when it sends something else. */
    public static final String ACCEPTED = Arrays.stream(values()).map(p -> p.param).collect(Collectors.joining(", "));

    private final String param;

    SummaryPeriod(String param) {
        this.param = param;
    }

    /** The period a request parameter names, or empty when it names none. Exact, lower-case match. */
    public static Optional<SummaryPeriod> fromParam(String value) {
        return Arrays.stream(values()).filter(p -> p.param.equals(value)).findFirst();
    }

    /** The period containing {@code now} on {@code zone}'s calendar, from its first instant to the next period's. */
    public TimeWindow window(Instant now, ZoneId zone) {
        LocalDate today = BusinessCalendar.localDate(now, zone);
        LocalDate first = switch (this) {
            case TODAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
        };
        LocalDate next = switch (this) {
            case TODAY -> first.plusDays(1);
            case WEEK -> first.plusWeeks(1);
            case MONTH -> first.plusMonths(1);
        };
        return new TimeWindow(BusinessCalendar.startOfDay(first, zone), BusinessCalendar.startOfDay(next, zone));
    }
}
