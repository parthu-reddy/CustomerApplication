DELETE FROM customers WHERE id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY phone_number ORDER BY id DESC) as rn
    FROM customers
  ) t WHERE t.rn > 1
);
ALTER TABLE customers ADD CONSTRAINT unique_phone_number UNIQUE (phone_number);
