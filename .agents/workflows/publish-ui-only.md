---
description: Commit, push, and build UI changes via GitHub Actions.
---
# Publish UI Only

This workflow orchestrates the UI publication process using GitHub Actions, isolating it from the backend services.

Run these steps in order, wait for each to complete, and check the output before proceeding.

## 1. Commit and push UI only

```bash
bash FoodDeliveryContracts/ci/commit_and_push.sh -m "your message here" --only FoodDeliveryAppUI
```
(If changes to Deployment/env_deployments/dev/*.env are needed, you can use `--only FoodDeliveryAppUI,Deployment`)

## 1.5. Enable Actions (Visibility)

```bash
bash FoodDeliveryContracts/ci/flip_visibility.sh --public
```

## 2. Build UI (GitHub Actions)

```bash
bash FoodDeliveryContracts/ci/build_services.sh --only FoodDeliveryAppUI
```
Wait for this to complete. It will trigger the UI GitHub action and push the newly built image to OCIR, tagged with the commit SHA.

## 3. Revert Visibility

```bash
bash FoodDeliveryContracts/ci/flip_visibility.sh --revert
```

*(Note: There are no contract tests for the UI, so Phase 1 and Phase 2 of the contract tests are skipped.)*
