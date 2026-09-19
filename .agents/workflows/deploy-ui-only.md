---
description: Deploy UI only using the image built by CI
---
# Deploy UI Only

Deploys the UI container to the Oracle VM using the Docker image that was previously built by GitHub Actions (via `/publish-ui-only` or `/publish-all-changes`). 
This allows you to easily deploy the UI without needing to run a full `/clean-deploy` or relying on a local build.

## 1. Update the image tag

The deployment script (`Deployment/deploy.sh`) relies on the tags pinned in `Deployment/.versions`.
Update `FOOD_DELIVERY_APP_UI_TAG` in `Deployment/.versions` to match the latest git commit SHA from the `FoodDeliveryAppUI` repository. 

You can find the latest commit SHA by running:
```bash
(cd FoodDeliveryAppUI && git rev-parse HEAD)
```

*(Note: Don't forget to commit and push the updated `.versions` file later to keep your deployment history in sync!)*

## 2. Deploy to VM

Once `.versions` is updated, deploy just the UI container:

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

## 3. Hard Refresh

Then **hard-refresh the browser** (`Cmd+Shift+R`). The SPA caches `index.html`, so a correct deploy looks like nothing happened until you force a refresh.
