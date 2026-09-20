---
description: Build and publish a single service image to the registry (local or CI)
---

# Publish One Service

Deployment and publishing are decoupled. You must build and publish an image before you can deploy it. 

There are two ways to publish a single service: the **Local Fast Path** (using your machine to build) or the **CI Path** (using GitHub Actions).

## Option A: Local Fast Path (Recommended for Agents)

Use this method to quickly build the jar/bundle, package it into a container image, push it to OCIR, and record the new tag locally in `Deployment/.versions`.

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
Deployment/publish.sh <service>
```

- This script **does not run Maven**. You must build the JAR first using `bash FoodDeliveryContracts/ci/build_verify.sh` or `mvn compile`.
- This script builds the docker image natively and pushes it to OCIR.
- **IMPORTANT**: The script modifies `Deployment/.versions` to track the new tag. You must commit and push this file, as its git history acts as the deployment history for rollbacks!

## Option B: CI Path (GitHub Actions)

If you need the full CI pipeline to build and publish the image, you can use the `--only` flag on the standard CI scripts. Run these in order:

```bash
# 1. Commit and push the changes (shows diffs and prompts before pushing)
bash FoodDeliveryContracts/ci/commit_and_push.sh -m "what actually changed" --only <DirectoryName>

# 2. Make repos public for GitHub Actions
bash FoodDeliveryContracts/ci/flip_visibility.sh --public

# 3. Trigger the Build and Push workflow for just this service
bash FoodDeliveryContracts/ci/build_services.sh --only <DirectoryName>

# 4. Restore repo visibility
bash FoodDeliveryContracts/ci/flip_visibility.sh --revert
```

*Note: The CI path requires directory names (e.g., `CustomerApplication`), whereas the local path requires compose service names (e.g., `customer-service`).*

## Next Steps

After the image is published and the new tag is recorded in `.versions`, you can deploy it to the VM. See [deploy-one-service.md](deploy-one-service.md).
