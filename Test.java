public class Test {
    public static void main(String[] args) {
        String payload = "\"{\\\"amount\\\": 300.00, \\\"orderId\\\": \\\"abc\\\"}\"";
        if (payload.startsWith("\"") && payload.endsWith("\"")) {
            payload = payload.substring(1, payload.length() - 1).replace("\\\"", "\"");
        }
        System.out.println(payload);
    }
}
