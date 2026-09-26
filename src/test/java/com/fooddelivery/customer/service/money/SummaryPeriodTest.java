package com.fooddelivery.customer.service.money;

import com.fooddelivery.common.time.TimeWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each period on an outlet's own calendar. The DST cases are the ones a fixed 24-hour day, 7-day week
 * or UTC month gets wrong; the Kolkata/Los Angeles pairs are the ones a server-zone calendar gets wrong.
 * Every expected instant was printed from the JDK's tzdata, not worked out by hand.
 */
class SummaryPeriodTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");

    private static TimeWindow window(SummaryPeriod period, String now, ZoneId zone) {
        return period.window(Instant.parse(now), zone);
    }

    private static TimeWindow expected(String from, String to) {
        return new TimeWindow(Instant.parse(from), Instant.parse(to));
    }

    @Test
    void theAcceptedValuesAreTodayWeekAndMonth() {
        assertEquals(Optional.of(SummaryPeriod.TODAY), SummaryPeriod.fromParam("today"));
        assertEquals(Optional.of(SummaryPeriod.WEEK), SummaryPeriod.fromParam("week"));
        assertEquals(Optional.of(SummaryPeriod.MONTH), SummaryPeriod.fromParam("month"));
        assertEquals("today, week, month", SummaryPeriod.ACCEPTED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "MONTH", "Month", " month", "year", "day", "7d"})
    void anythingElseIsNotAPeriod(String value) {
        assertEquals(Optional.empty(), SummaryPeriod.fromParam(value));
    }

    // ---- today ----

    @Test
    void todayOnTheFallBackDayIs25Hours() {
        TimeWindow today = window(SummaryPeriod.TODAY, "2026-11-01T15:00:00Z", NEW_YORK);

        assertEquals(expected("2026-11-01T04:00:00Z", "2026-11-02T05:00:00Z"), today);
        assertEquals(Duration.ofHours(25), today.length());
    }

    @Test
    void todayOnTheSpringForwardDayIs23Hours() {
        TimeWindow today = window(SummaryPeriod.TODAY, "2026-03-08T15:00:00Z", NEW_YORK);

        assertEquals(expected("2026-03-08T05:00:00Z", "2026-03-09T04:00:00Z"), today);
        assertEquals(Duration.ofHours(23), today.length());
    }

    /** Chile springs forward at midnight, so 6 September 2026 starts at 01:00 there. */
    @Test
    void todayStartsAtTheFirstInstantThatExistsWhenMidnightDoesNot() {
        TimeWindow today = window(SummaryPeriod.TODAY, "2026-09-06T15:00:00Z", SANTIAGO);

        assertEquals(expected("2026-09-06T04:00:00Z", "2026-09-07T03:00:00Z"), today);
    }

    /** 20:00Z is 01:30 on the 26th in Kolkata and 13:00 on the 25th in Los Angeles. */
    @Test
    void todayIsTheOutletsDateNotTheServers() {
        assertEquals(expected("2026-09-25T18:30:00Z", "2026-09-26T18:30:00Z"),
                window(SummaryPeriod.TODAY, "2026-09-25T20:00:00Z", KOLKATA));
        assertEquals(expected("2026-09-25T07:00:00Z", "2026-09-26T07:00:00Z"),
                window(SummaryPeriod.TODAY, "2026-09-25T20:00:00Z", LOS_ANGELES));
    }

    // ---- week ----

    @Test
    void theWeekOverTheFallBackRunsMondayToMondayAnd169Hours() {
        TimeWindow week = window(SummaryPeriod.WEEK, "2026-10-21T12:00:00Z", LONDON);

        assertEquals(expected("2026-10-18T23:00:00Z", "2026-10-26T00:00:00Z"), week);
        assertEquals(Duration.ofHours(169), week.length());
    }

    @Test
    void theWeekOverTheSpringForwardIs167Hours() {
        TimeWindow week = window(SummaryPeriod.WEEK, "2026-03-05T17:00:00Z", NEW_YORK);

        assertEquals(expected("2026-03-02T05:00:00Z", "2026-03-09T04:00:00Z"), week);
        assertEquals(Duration.ofHours(167), week.length());
    }

    @Test
    void sundayNightIsStillThisWeekAndMondayMidnightStartsTheNext() {
        TimeWindow thisWeek = expected("2026-10-18T23:00:00Z", "2026-10-26T00:00:00Z");

        assertEquals(thisWeek, window(SummaryPeriod.WEEK, "2026-10-25T23:59:59Z", LONDON));
        assertEquals(expected("2026-10-26T00:00:00Z", "2026-11-02T00:00:00Z"),
                window(SummaryPeriod.WEEK, "2026-10-26T00:00:00Z", LONDON));
    }

    // ---- month ----

    @Test
    void aMonthWithTheFallBackIs31DaysAndAnHour() {
        TimeWindow month = window(SummaryPeriod.MONTH, "2026-10-15T12:00:00Z", LONDON);

        assertEquals(expected("2026-09-30T23:00:00Z", "2026-11-01T00:00:00Z"), month);
        assertEquals(Duration.ofHours(31 * 24 + 1), month.length());
    }

    @Test
    void aMonthWithTheSpringForwardIs31DaysLessAnHour() {
        TimeWindow month = window(SummaryPeriod.MONTH, "2026-03-20T12:00:00Z", NEW_YORK);

        assertEquals(expected("2026-03-01T05:00:00Z", "2026-04-01T04:00:00Z"), month);
        assertEquals(Duration.ofHours(31 * 24 - 1), month.length());
    }

    /** At 20:00Z on 30 September, Kolkata is already in October and Los Angeles is not. */
    @Test
    void theMonthTurnsOnTheOutletsCalendar() {
        assertEquals(expected("2026-09-30T18:30:00Z", "2026-10-31T18:30:00Z"),
                window(SummaryPeriod.MONTH, "2026-09-30T20:00:00Z", KOLKATA));
        assertEquals(expected("2026-09-01T07:00:00Z", "2026-10-01T07:00:00Z"),
                window(SummaryPeriod.MONTH, "2026-09-30T20:00:00Z", LOS_ANGELES));
    }

    // ---- every period ----

    /** Consecutive periods tile with no gap or overlap, and each contains the instant it was asked about. */
    @ParameterizedTest
    @EnumSource(SummaryPeriod.class)
    void periodsTileAcrossEveryTransitionAndContainNow(SummaryPeriod period) {
        List<String> nows = List.of("2026-03-08T06:59:59Z", "2026-03-08T07:00:00Z", "2026-11-01T05:30:00Z",
                "2026-11-01T06:30:00Z", "2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z", "2026-09-06T04:00:00Z");
        for (ZoneId zone : List.of(NEW_YORK, LONDON, SANTIAGO, KOLKATA, LOS_ANGELES)) {
            for (String now : nows) {
                TimeWindow current = window(period, now, zone);
                TimeWindow next = period.window(current.to(), zone);
                assertTrue(current.contains(Instant.parse(now)), period + " in " + zone + " at " + now + ": " + current);
                assertEquals(current.to(), next.from(), period + " in " + zone + " at " + now);
            }
        }
    }
}
