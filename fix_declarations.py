import re

with open("src/main/java/com/fooddelivery/order/service/OrderSagaOrchestrator.java", "r") as f:
    content = f.read()

# Add transactionTemplate
insert_str = """    private final DoubleEntryLedgerService ledgerService;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;"""

content = content.replace("    private final DoubleEntryLedgerService ledgerService;\n    private final org.springframework.web.client.RestTemplate restTemplate;", insert_str)

with open("src/main/java/com/fooddelivery/order/service/OrderSagaOrchestrator.java", "w") as f:
    f.write(content)

