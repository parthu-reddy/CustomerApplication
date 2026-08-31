---
description: steps to initialize new deployment
---

Adding a **new service** to the deployment. Read these and follow them:

- `Deployment/DeploymentSteps/GENERALIZED_DEPLOYMENT_STEPS.md` — the full checklist, steps 1–7
- `Deployment/DatabaseInitializationApproach` — how its database is created
- `CommonMistakesDocumentation/Deployment` — mistakes already made once
- `Deployment/SCHEMA_POLICY.md` — migrations are immutable and forward-only

## The checklist, short form

1. **Dockerfile** in the service directory, using the layered `jarmode=tools` extract.
2. **`Deployment/<service-name>.yml`** — config lives here, served by `config-service`, not in the
   service's own `application.yml`.
3. **`Deployment/docker-compose.yml`** — add the service, including a `mem_limit` and a healthcheck.
   All 27 existing services have both.
4. **`Deployment/init-multiple-dbs.sql`** — create its database.
5. **`Deployment/api-gateway.yml`** — add its route if it takes external traffic.
6. **`Deployment/service-map.tsv`** — one tab-separated row:
   `<ModuleDir>\t<compose-service>\t<build-context>\t<Dockerfile path relative to that context>`.
   **`publish.sh` and `deploy.sh` both refuse a service that is not listed here.** This is the step
   most easily missed, and it is the only place the build context is recorded.
7. **Root `pom.xml`** — add it under `<modules>`.

If it owns a database, also add it to:

- `Deployment/OracleDeployment/DummyData/reset_remote_db.sh` (`SERVICES` and `DBS`) — a database
  whose owner is never restarted is never rebuilt after a wipe.
- `Deployment/OracleDeployment/DummyData/schema_sentinels.tsv` — one sentinel table, so the wait
  actually waits for it.
- Recreate any extension it needs; `DROP SCHEMA public CASCADE` drops extensions with the schema.

## Then deploy it

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
Deployment/ship.sh <service-name>
```

Verify: `Deployment/reconcile.sh` reports nothing undeclared and no image drift, the service appears
in Eureka on 8761, and its log shows `Started <App> in Ns` with no exceptions. Healthy is not the
same as error-free.
