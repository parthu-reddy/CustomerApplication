---
description: deploy a single service to oracle
---

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
Deployment/ship.sh <service>
```

One command: builds the jar (or the UI bundle), publishes the image to OCIR, deploys it to the VM,
waits for health, and verifies the container ended up on the intended image. Backend and UI use the
same command — `ship.sh` picks Maven or npm from `Deployment/service-map.tsv`.

- `--fresh` also recreates the container from scratch. It does not touch the database.
- `--no-build` skips the build when the jar is already current.
- `Deployment/ship.sh` with no arguments lists every valid service name.

Service names are compose names, not directory names — `chat-service`, not `CommunicationService`.

Afterwards, read the log and confirm it booted cleanly; healthy is not the same as error-free:

```bash
ssh -i $SSH_KEY ubuntu@140.245.234.137 "cd 'Food Delivery.nosync/Deployment' && docker compose logs --tail=100 <service>"
```

To undo: `Deployment/deploy.sh --rollback <service>`.
