---
description: Publish Dev profile configuration to the Oracle VM and restart its readers
---

# Deploy Dev Profile Configuration

Use this for changes to `Deployment/*.yml` that do not change application code or container images.
The Oracle stack must stay on the `dev` profile. The deployment command verifies that profile before
publishing, checks each copied file, and waits for each restarted service to become healthy.

From the workspace root, preview the exact files and restart order:

```bash
Deployment/deploy.sh --config --dry-run application-dev.yml api-gateway.yml api-gateway-dev.yml identity-service-dev.yml
```

Review the printed targets. A change to `application-dev.yml` applies to every Spring config client,
so those services restart one at a time; the API gateway restarts last. To apply the Dev rate-limit
configuration and the current gateway routes, publish the same four files:

```bash
Deployment/deploy.sh --config --yes application-dev.yml api-gateway.yml api-gateway-dev.yml identity-service-dev.yml
```

`--yes` is appropriate only after reviewing the dry-run list. This workflow does not build images,
reset databases, or change the active profile. Do not use `--wipe` or run the dummy-data reset for a
configuration-only release.

After the command completes, inspect the restarted services' logs and run:

```bash
python3 Deployment/validate_hardening_phase1.py --remote
Deployment/reconcile.sh
```

The remote validator checks that all local config YAML files reached the VM and that `.env` remains
mode `600`. Reconcile checks declared services and image drift.
