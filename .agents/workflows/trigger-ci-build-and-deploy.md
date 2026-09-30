---
description: Trigger GitHub Actions CI build and deploy the newly built images
---

Use [publish-all-changes.md](publish-all-changes.md) for reviewed commits, CI builds, and contract
checks. Use [deploy-one-service.md](deploy-one-service.md) or [clean-deploy.md](clean-deploy.md)
for image deployment after CI finishes.

This CI path is for code changes that need new images. A config-only change under `Deployment/*.yml`
does not need CI or a container build; use [deploy-dev-profile-config.md](deploy-dev-profile-config.md).
Do not use the old `git add .` / unfiltered latest-run commands or add `--wipe` to the deployment.

CI is dispatched per repository. Service applications use each repository's `build-and-push.yml`;
the shared base dependencies use `publish.yml`. Do not invoke the retired
`FoodDeliveryContracts/build-and-publish.yml` workflow.

Use the checked-in dispatcher from the workspace root. Its `--only` values are workspace directory
names, not Compose service names:

```bash
bash FoodDeliveryContracts/ci/build_services.sh --only CustomerApplication,FoodDeliveryAppUI
```

If the change is in a repo **other than** `FoodDeliveryContracts` (e.g. `CustomerApplication`), push
that repo too — the CI checks out every repo at HEAD.

## What to know

- **All commands run from the workspace root** (`Food Delivery.nosync/`), not from inside repos.
- **The profile is already `dev`** on the VM (`SPRING_PROFILES_ACTIVE=dev` in `.env`). Do not set it.
- **The VM environment is Vault-materialized.** `Deployment/.env` is ignored and must never be
  committed, downloaded, or copied from CI. CI records reviewed image tags under
  `Deployment/env_deployments/dev/`; update and review that checkout before deployment.
- **Hard-refresh the browser** (`Cmd+Shift+R`) after deploying — the SPA caches `index.html`.

## When to use this vs other workflows

- **Only one service changed and Docker Desktop is running locally?** Use `Deployment/ship.sh <service>`
  instead — see [deploy-one-service.md](deploy-one-service.md). Much faster (~1 minute).
- **Only the UI changed?** See [deploy-ui-only.md](deploy-ui-only.md).
- **Need to rebuild schemas and reload dummy data?** See [clean-and-create-dummy-data.md](clean-and-create-dummy-data.md).
