---
description: Trigger GitHub Actions CI build and deploy the newly built images
---

```bash
# 1. Push changes (from whichever repo was modified)
(cd FoodDeliveryContracts && git add . && git commit -m "chore: <describe>" && git push)

# 2. Trigger the CI build
gh workflow run build-and-publish.yml -f services="all" -R parthu-reddy/FoodDeliveryContracts

# 3. Monitor (blocks until complete)
RUN_ID=$(gh run list -R parthu-reddy/FoodDeliveryContracts -L 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" -R parthu-reddy/FoodDeliveryContracts

# 4. Deploy the updated env file
(cd Deployment && git pull)
export REGISTRY=hyd.ocir.io/axekmbadoczl
bash Deployment/OracleDeployment/03_clean_deploy.sh
```

The `build-and-publish` workflow is `workflow_dispatch`-only — pushes do NOT auto-trigger it.
It always builds the entire Maven reactor + UI bundle (~9 minutes). The `services` input only
controls which Docker images get published to OCIR, not what gets compiled.

Service names are **compose service names** from `Deployment/service-map.tsv` (e.g. `customer-service`,
not `CustomerApplication`). To publish a subset, space-separate them:

```bash
gh workflow run build-and-publish.yml -f services="customer-service food-delivery-app-ui" -R parthu-reddy/FoodDeliveryContracts
```

If the change is in a repo **other than** `FoodDeliveryContracts` (e.g. `CustomerApplication`), push
that repo too — the CI checks out every repo at HEAD.

## What to know

- **All commands run from the workspace root** (`Food Delivery.nosync/`), not from inside repos.
- **The profile is already `dev`** on the VM (`SPRING_PROFILES_ACTIVE=dev` in `.env`). Do not set it.
- **The env file is committed** directly to the repository by the CI.
  You must download it with `gh run download` before deploying.
- **Hard-refresh the browser** (`Cmd+Shift+R`) after deploying — the SPA caches `index.html`.

## When to use this vs other workflows

- **Only one service changed and Docker Desktop is running locally?** Use `Deployment/ship.sh <service>`
  instead — see [deploy-one-service.md](deploy-one-service.md). Much faster (~1 minute).
- **Only the UI changed?** See [deploy-ui-only.md](deploy-ui-only.md).
- **Need to rebuild schemas and reload dummy data?** See [clean-and-create-dummy-data.md](clean-and-create-dummy-data.md).
