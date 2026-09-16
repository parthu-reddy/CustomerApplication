---
description: Automate committing, publishing to public repos, running workflows, and reverting to private.
---

# Automated Commit, Publish, and Clean Deploy Workflow

This script automates committing local changes, triggering GitHub Actions, and managing a clean deploy. It intelligently detects if a core dependency has changed and forces a rebuild of all dependent microservices.

### Instructions:
1. Save the following script as `publish-and-deploy.sh` in the root of your workspace.
2. Run it with `bash publish-and-deploy.sh`

```bash
#!/bin/bash
set -e

echo "Starting automated publish and deploy workflow..."

# 1. Identify all repositories with uncommitted changes
MODIFIED_REPOS=()
echo "Checking for local changes..."
for dir in */; do
  if [ -d "$dir/.git" ]; then
    repo_dir="${dir%/}"
    cd "$repo_dir"
    changes=$(git status --porcelain)
    if [ -n "$changes" ]; then
      echo "Committing changes in $repo_dir"
      git add .
      git commit -m "chore: automated workflow update" || true
      git pull --rebase origin main || true
      git push origin main
      cd ..
      MODIFIED_REPOS+=("$repo_dir")
    else
      cd ..
    fi
  fi
done

if [ ${#MODIFIED_REPOS[@]} -eq 0 ]; then
  echo "No local changes found in any repository. Exiting."
  exit 0
fi

echo "Modified repositories: ${MODIFIED_REPOS[*]}"

# 2. Dependency expansion
DEPENDENCIES=("FoodDeliveryParentPOM" "IdentitySigning" "CommonLibrary" "FoodDeliveryContracts")
NEEDS_REBUILD=false

for repo in "${MODIFIED_REPOS[@]}"; do
  for dep in "${DEPENDENCIES[@]}"; do
    if [ "$repo" == "$dep" ]; then
      NEEDS_REBUILD=true
      break 2
    fi
  done
done

if [ "$NEEDS_REBUILD" = true ]; then
  echo "Base dependency changed. Expanding to rebuild ALL dependent microservices."
  ALL_SERVICES=(
    "ApiGateway" "BiddingEngine" "BudgetLimitingService" "CampaignService"
    "CommunicationIntegration" "CommunicationService" "ConfigService"
    "CustomerApplication" "DeliveryExecutiveApplication" "EurekaServer"
    "FoodDeliveryAppUI" "GovernmentIDValidationService" "IdentityService"
    "LedgerService" "MapsIntegration" "ONDCIntegrationService"
    "PaymentGatewayIntegration" "RestaurantApplication" "ReviewsService"
    "UserTrackingService" "WalletService"
  )
  for svc in "${ALL_SERVICES[@]}"; do
    if [[ ! " ${MODIFIED_REPOS[*]} " =~ " ${svc} " ]]; then
      MODIFIED_REPOS+=("$svc")
    fi
  done
  echo "Full rebuild list: ${MODIFIED_REPOS[*]}"
fi

# 3. Make repositories public
echo "Making repositories public to enable GH Actions..."
for repo_dir in "${MODIFIED_REPOS[@]}"; do
  (cd "$repo_dir" && gh repo edit --visibility public --accept-visibility-change-consequences)
done

# 4. Trigger and watch workflows
rm -f .run_ids.txt

trigger_and_collect() {
  local target_repo=$1
  if [[ ! " ${MODIFIED_REPOS[*]} " =~ " ${target_repo} " ]]; then return; fi
  
  echo "Triggering workflows for $target_repo..."
  (cd "$target_repo"
  local count=0
  for workflow in .github/workflows/*.yml; do
    if [ -f "$workflow" ]; then
      workflow_name=$(basename "$workflow")
      echo "  -> triggering $workflow_name"
      gh workflow run "$workflow_name"
      count=$((count + 1))
    fi
  done
  
  if [ $count -gt 0 ]; then
    # Wait briefly for GH to register the run
    sleep 5
    
    # Fetch the most recent run IDs corresponding to the workflows we just triggered
    RUN_IDS=$(gh run list -L "$count" --json databaseId -q '.[].databaseId')
    
    for run_id in $RUN_IDS; do
      echo "$target_repo $run_id" >> ../.run_ids.txt
    done
  fi)
}

watch_collected_runs() {
  if [ -f .run_ids.txt ]; then
    while read -r target_repo run_id; do
      echo "Watching run $run_id in $target_repo until completion..."
      (cd "$target_repo" && gh run watch "$run_id")
    done < .run_ids.txt
    rm -f .run_ids.txt
  fi
}

# Run parents first
echo "Building base dependencies first..."
for dep in "${DEPENDENCIES[@]}"; do
  trigger_and_collect "$dep"
done
watch_collected_runs

# Run all other services
echo "Building all downstream services in parallel..."
for repo in "${MODIFIED_REPOS[@]}"; do
  if [[ " ${DEPENDENCIES[*]} " =~ " ${repo} " ]]; then continue; fi
  trigger_and_collect "$repo"
done
watch_collected_runs

# 5. Make repositories private again
echo "Reverting repositories to private..."
for repo_dir in "${MODIFIED_REPOS[@]}"; do
  (cd "$repo_dir" && gh repo edit --visibility private --accept-visibility-change-consequences)
done

# 6. Clean deploy with complete wipe on Oracle VM
if [ "$NEEDS_REBUILD" = true ]; then
  echo "Executing clean deploy and complete wipe on Oracle VM..."
  
  # GH actions updated .versions on the remote branch of Deployment. 
  # We must pull it locally so deploy.sh sees the new image tags.
  echo "Pulling latest .versions from Deployment repo..."
  (cd Deployment && git pull --rebase origin main)
  
  echo "Deploying newly built images..."
  (cd Deployment && ./deploy.sh --all)
  
  echo "Running dummy-data.sh to completely wipe and reseed databases..."
  (cd Deployment && ./dummy-data.sh --all --yes)
  
  echo "Clean deploy finished successfully!"
fi

echo "Workflow complete."
```

## What to know
- **Visibility changes** expose the source code to the internet temporarily. Only do this if strictly necessary (e.g., to preserve private GitHub Action minutes).
- **Dependency Order:** Always trigger upstream contracts and shared libraries before dependent downstream services.
- **Continuous Monitoring:** Do not assume the pipeline succeeds; actively watch the output so you can fix any broken tests before finalizing the deployment.
