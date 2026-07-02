-- Remove duplicates keeping one
DELETE FROM ledger_accounts a USING (
    SELECT id,
           ROW_NUMBER() OVER(PARTITION BY owner_id, owner_type ORDER BY id) as rn
    FROM ledger_accounts
) b
WHERE a.id = b.id AND b.rn > 1;

ALTER TABLE ledger_accounts
ADD CONSTRAINT uk_ledger_account_owner UNIQUE (owner_id, owner_type);
