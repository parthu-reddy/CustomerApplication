-- Addresses previously had only a display city (for example, "Bengaluru"). Fleet and dispatch
-- need the same canonical operating-area key used by drivers, so old records are deliberately
-- backfilled to the source-backed single deployment scope. Do not infer a city from free-form
-- address text or coordinates: that would silently misclassify data at a boundary.
ALTER TABLE customer_addresses
    ADD COLUMN IF NOT EXISTS city_id VARCHAR(64);

UPDATE customer_addresses
SET city_id = 'BLR'
WHERE city_id IS NULL;

ALTER TABLE customer_addresses
    ALTER COLUMN city_id SET NOT NULL;

ALTER TABLE customer_addresses
    ADD CONSTRAINT ck_customer_addresses_city_id_canonical
    CHECK (city_id ~ '^[A-Z][A-Z0-9_-]{0,63}$');

CREATE INDEX IF NOT EXISTS idx_customer_addresses_city_id
    ON customer_addresses (city_id, id);
