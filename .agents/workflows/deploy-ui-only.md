---
description: Deploy UI only using the image built by CI
---
# Deploy UI Only

Deploys the UI container to the Oracle VM using the Docker image that was previously built by GitHub Actions (via `/publish-ui-only` or `/publish-all-changes`). 
This allows you to easily deploy the UI without needing to run a full `/clean-deploy` or relying on a local build.

## 1. Ensure latest tags are pulled

The GitHub Actions workflow automatically updates the image tag in `Deployment/env_deployments/dev/food-delivery-app-ui.env` after a successful build. 
The deployment script (`deploy.sh`) will automatically pull this update as long as there are no uncommitted local changes in the `Deployment` repository.

Ensure your `Deployment` repository has no uncommitted changes (especially manual tag updates).

## 2. Deploy to VM

Deploy just the UI container:

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
cd Deployment
./deploy.sh food-delivery-app-ui
```

`deploy.sh` will:
- Read the new tag from `.versions`.
- Connect to the VM and pull the updated image from OCIR.
- Recreate the `food-delivery-app-ui` container.
- Verify that it successfully started with the correct image.

## 3. Apply Dev Profile Configuration

**CRITICAL RULE FOR AGENT:** After deploying the UI, you MUST also apply the dev profile
configuration to ensure all services remain on the correct dev settings.

```bash
Deployment/deploy.sh --config --dry-run application-dev.yml api-gateway.yml api-gateway-dev.yml identity-service-dev.yml
```

Review the dry-run output, then apply:

```bash
Deployment/deploy.sh --config --yes application-dev.yml api-gateway.yml api-gateway-dev.yml identity-service-dev.yml
```

Then validate:

```bash
python3 Deployment/validate_hardening_phase1.py --remote
Deployment/reconcile.sh
```

## 4. Hard Refresh

Then **hard-refresh the browser** (`Cmd+Shift+R`). The SPA caches `index.html`, so a correct deploy looks like nothing happened until you force a refresh.
