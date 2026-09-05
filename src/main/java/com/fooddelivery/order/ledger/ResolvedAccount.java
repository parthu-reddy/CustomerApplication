package com.fooddelivery.order.ledger;

import com.fooddelivery.common.enums.LedgerAccountType;
import java.util.UUID;
import lombok.Value;

@Value
public class ResolvedAccount {
    LedgerAccountType type;
    UUID id;
}
