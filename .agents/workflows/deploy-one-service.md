---
description: Deploy a single service to Oracle VM
---

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
Deployment/deploy.sh <service>
```

Deploys a single service to the Oracle VM. **This script does not build or publish.** It reads the tag recorded in `Deployment/.versions`, pulls that specific image onto the VM, restarts the container, and waits for it to become healthy.

If you need to build and publish a new image first, see `publish-one-service.md`.

- `--fresh` recreating the container from scratch (does not touch the database).
- `Deployment/deploy.sh` with no arguments prints the usage.

Service names are compose names, not directory names — `chat-service`, not `CommunicationService`.

### Verification

**CRITICAL RULE FOR AGENT:** After deploying, read the log and confirm it booted cleanly; "healthy" is not the same as error-free.

```bash
ssh -i $SSH_KEY ubuntu@140.245.234.137 "cd 'Food Delivery.nosync/Deployment' && docker compose logs --tail=100 <service>"
```

### Rollback

To undo a deployment and revert to the previous tag recorded in the git history of `.versions`:
```bash
Deployment/deploy.sh --rollback <service>
```
