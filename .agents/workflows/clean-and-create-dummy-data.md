---
description: clean database in oracle and recreate dummy data
---

```bash
Deployment/dummy-data.sh              # wipe every database, let Flyway rebuild, then load
Deployment/dummy-data.sh --load-only  # load into the existing schemas, no wipe
```

It prompts before wiping (twelve databases on the live VM, no backup). `--yes` skips the prompt.

Users are created first — `identity_db` is populated before anything referencing those ids. That
order is enforced by the script; do not reorder it.

No password is needed: `psql` runs inside the postgres container and reads the credential from that
container's own environment.

## What to know

- **Flyway rebuilds the schemas, not Hibernate.** Every service runs `ddl-auto: validate`. Dropping
  the schema also drops `flyway_schema_history`, so each service re-applies every migration from V1
  on restart. If a service does not come back, read its log — a failed migration stops Flyway and
  the table never appears.
- The wait is on sentinel tables (`DummyData/schema_sentinels.tsv`), bounded by a timeout, not a
  fixed sleep.
- **Redis is flushed**, so no driver shows online until `remote_rider_simulator.py` is running.
- `reviews_db` and `ondc_db` are skipped — those services are parked and would never re-migrate.

## After it finishes

Verify rather than trusting the exit code — confirm `identity_db.users`, `customer_db.customers`,
`delivery_db.delivery_executives` and `restaurant_db.brands` are non-empty, and that a customer id
resolves to a user in `identity_db`. A load against a half-migrated schema can succeed and still
leave the data unusable.

Last verified run: 540 users, 500 customers, 30 delivery executives, 10 brands; all 500 customer
ids and all 30 rider ids resolved in `identity_db`.
