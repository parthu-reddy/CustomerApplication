-- `attempts` is a refund's full dispatch history and never resets. The stuck-refund sweeper's
-- give-up budget belongs to one queueing: an admin retry of a FAILED refund must not inherit the
-- exhausted budget of the attempt that failed, or the sweeper fails it again on its first pass.
ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sweep_attempts INT NOT NULL DEFAULT 0;
