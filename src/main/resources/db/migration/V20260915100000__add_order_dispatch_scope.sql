ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS dispatch_city_id VARCHAR(64);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS fleet_search_radius_km DOUBLE PRECISION;

UPDATE orders
SET dispatch_city_id = 'BLR'
WHERE dispatch_city_id IS NULL;

UPDATE orders
SET fleet_search_radius_km = 5.0
WHERE fleet_search_radius_km IS NULL;

ALTER TABLE orders
    ALTER COLUMN dispatch_city_id SET NOT NULL,
    ALTER COLUMN fleet_search_radius_km SET NOT NULL;
