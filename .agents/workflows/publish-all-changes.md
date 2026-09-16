---
description: Automate committing, publishing to public repos, running workflows, and reverting to private.
---

# Publish All Changes Workflow

This workflow automates the process of committing local changes across all microservices, temporarily making repositories public to run CI/CD GitHub Actions, and then securing them by making them private again.

```bash
# 1. Identify all repositories with uncommitted changes
MODIFIED_REPOS=()
for dir in */; do
  if [ -d "$dir/.git" ]; then
    (cd "$dir" && changes=$(git status --porcelain)
    if [ -n "$changes" ]; then
      echo "Committing changes in $dir..."
      git add .
      git commit -m "chore: automated workflow update"
      git pull --rebase origin main
      git push
      MODIFIED_REPOS+=("${dir%/}")
    fi)
  fi
done

# 2. Make modified repositories public, trigger workflows, and revert
for repo in "${MODIFIED_REPOS[@]}"; do
  echo "Processing $repo..."
  
  # Make public
  gh repo edit "parthu-reddy/$repo" --visibility public --accept-visibility-change-consequences
  
  # Trigger all workflows
  (cd "$repo" && for workflow in .github/workflows/*.yml; do
    if [ -f "$workflow" ]; then
      workflow_name=$(basename "$workflow")
      echo "Triggering $workflow_name in $repo..."
      gh workflow run "$workflow_name"
    fi
  done)
  
  # Note: To block and wait for completion, you can use:
  # gh run watch $(gh run list -R "parthu-reddy/$repo" -L 1 --json databaseId -q '.[0].databaseId')
  
  # Make private again
  # In a fully automated script, you might want to wait for workflows to finish first before making private
  gh repo edit "parthu-reddy/$repo" --visibility private --accept-visibility-change-consequences
done

# 3. Test Contracts Repository (Always run at the end)
echo "Testing FoodDeliveryContracts..."
gh repo edit parthu-reddy/FoodDeliveryContracts --visibility public --accept-visibility-change-consequences
(cd FoodDeliveryContracts && gh workflow run contract-verification.yml)
gh repo edit parthu-reddy/FoodDeliveryContracts --visibility private --accept-visibility-change-consequences
```

## What to know
- **Visibility changes** expose the source code to the internet temporarily. Only do this if strictly necessary (e.g., to preserve private GitHub Action minutes).
- **Dependency Order:** Always trigger upstream contracts and shared libraries before dependent downstream services.
- **Continuous Monitoring:** Do not assume the pipeline succeeds; actively watch the output so you can fix any broken tests before finalizing the deployment.
