---
description: Automate committing, publishing to public repos, running workflows, and reverting to private.
---

# Publish All Changes Workflow

This workflow automates the process of committing local changes across all microservices, temporarily making repositories public to run CI/CD GitHub Actions, and then securing them by making them private again.

```bash
# 1. Identify all repositories with uncommitted changes
for dir in */; do
  if [ -d "$dir/.git" ]; then
    (cd "$dir" && changes=$(git status --porcelain)
    if [ -n "$changes" ]; then
      echo "Committing changes in $dir..."
      git add -u
      git commit -m "chore: automated workflow update"
      git pull --rebase origin main
      git push
    fi)
  fi
done

# 2. Make modified repositories public (Requires Accept Consequences Flag)
# Example for CommunicationService
gh repo edit parthu-reddy/CommunicationService --visibility public --accept-visibility-change-consequences

# 3. Trigger Workflows in Dependency Order
# E.g., Contract tests before builds
(cd CommunicationService && gh workflow run contract-tests.yml && gh workflow run build-and-push.yml)

# 4. Monitor Workflows
# Use `gh run watch` to block until success or failure
gh run watch <RUN_ID>

# 5. Fix Issues
# If monitoring reveals failures, investigate logs, patch codebase, and repeat steps 1-4.

# 6. Make Repositories Private Again
gh repo edit parthu-reddy/CommunicationService --visibility private --accept-visibility-change-consequences
```

## What to know
- **Visibility changes** expose the source code to the internet temporarily. Only do this if strictly necessary (e.g., to preserve private GitHub Action minutes).
- **Dependency Order:** Always trigger upstream contracts and shared libraries before dependent downstream services.
- **Continuous Monitoring:** Do not assume the pipeline succeeds; actively watch the output so you can fix any broken tests before finalizing the deployment.
