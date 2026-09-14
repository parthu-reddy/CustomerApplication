package com.fooddelivery.order.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every event this listener acts on must also be bindable.
 *
 * <p>ORDER_DRIVER_REJECTED was not. It has a real branch -- it clears the assigned driver and emits
 * a status sync -- but no EVENT_CLASSES entry, so the listener bound nothing, found no orderId, and
 * logged "Missing orderId. Ignored." Driver rejections never reached the order, and nothing failed.
 *
 * <p>Two maps that must agree, edited in different places, with the failure mode being silence: a
 * test is the only thing that keeps them together.
 */
public class OrderEventBindingCoverageTest {

    private static final Path CONSUMER =
            Paths.get("src/main/java/com/fooddelivery/order/service/OrderEventConsumer.java");

    @Test
    public void everyHandledEventTypeIsBindable() throws IOException {
        String src = Files.readString(CONSUMER)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\n]*", "");

        Set<String> bindable = collect(src, "EVENT_CLASSES\\.put\\(EventType\\.([A-Z_]+)");
        Set<String> viaHandlerMap = collect(src, "EVENT_HANDLERS\\.put\\(EventType\\.([A-Z_]+)");
        Set<String> viaExplicitBranch =
                collect(src, "EventType\\.([A-Z_]+)\\.name\\(\\)\\.equals\\(eventType\\)");

        assertTrue(bindable.size() >= 15,
                "parsed only " + bindable.size() + " EVENT_CLASSES entries -- this test's regex "
                        + "broke, not the code. Fix the test before trusting it.");

        Set<String> actedOn = new HashSet<>(viaHandlerMap);
        actedOn.addAll(viaExplicitBranch);

        List<String> gaps = new ArrayList<>();
        for (String e : actedOn) {
            if (!bindable.contains(e)) {
                gaps.add(e);
            }
        }
        assertTrue(gaps.isEmpty(),
                "These event types have a handler but no EVENT_CLASSES entry, so the listener "
                        + "cannot bind them and drops them with 'Missing orderId. Ignored.': " + gaps);
    }

    private static Set<String> collect(String src, String regex) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile(regex).matcher(src);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
