---
description: create a clean fresh deployment on oracle
---

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
Deployment/OracleDeployment/03_clean_deploy.sh          # keeps the databases
Deployment/OracleDeployment/03_clean_deploy.sh --wipe   # destroys volumes too (prompts)
```

Deploys every service: syncs the image tags to the VM, starts infrastructure, waits for Postgres,
then deploys config + discovery first and the remaining 17 services second. Starting 27 JVMs at
once on a 4-core box drove load average past 120, which is why it runs in waves. It finishes by
reconciling declared against running.

`--wipe` destroys all data. Recreate it afterwards with `Deployment/dummy-data.sh`.

To deploy just one service, use `Deployment/ship.sh <service>` instead — see
[deploy-one-service.md](deploy-one-service.md).

## How it works

The VM builds nothing. It holds only `Deployment/` — no source, no jars. Images are built on this
Mac (or by CI), pushed to OCIR tagged by git sha, and pulled by the VM.

The profile is already `dev` (`SPRING_PROFILES_ACTIVE=dev` in the VM's `.env`); do not set it
per-deploy.

## Mistakes to avoid

- **Trusting "healthy" as "correct".** `deploy.sh` verifies each container ended up on the image it
  was told to run. A deploy that pulls nothing and changes nothing still reports healthy.
- **Publishing a stale jar.** `publish.sh` refuses a jar older than its sources — `mvn compile`
  produces no jar, and publishing after it once shipped an image without the change.
- **Config changes do not travel in the image.** `config-service` bind-mounts the VM's
  `Deployment/` directory, so editing `<service>.yml` means rsyncing it and restarting the readers.
- **Migrations are immutable.** Never edit an applied one; add a new one. Flyway runs with
  `validate-on-migrate`, and `ddl-auto: validate` means Hibernate creates nothing.

**CRITICAL RULE FOR AGENT:** after deploying, read each service's logs yourself. Healthy is not
error-free — Flyway validation failures, bean creation errors and pool exhaustion all happen after
the container reports up. Then run `Deployment/reconcile.sh` and confirm nothing is undeclared and
nothing has drifted.

Record anything you had to fix in `Deployment/DeploymentSteps/GENERALIZED_DEPLOYMENT_STEPS.md`.
