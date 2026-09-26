-- Cash on delivery is not allowed (retired 2026-09-16; removed completely on 2026-09-26).
--
-- V20260916100000 made orders.payment_method and payment_intents.payment_method prepaid-only but left
-- payment_intents.status unconstrained, so the database would still take PENDING_COLLECTION and
-- COLLECTED -- statuses that only ever described a COD intent. restaurant_orders.payment_status
-- already refuses them (V20260916101000 in RestaurantApplication); this makes Customer agree.
ALTER TABLE payment_intents
    ADD CONSTRAINT chk_payment_intents_status_prepaid
    CHECK (status NOT IN ('PENDING_COLLECTION', 'COLLECTED'));
