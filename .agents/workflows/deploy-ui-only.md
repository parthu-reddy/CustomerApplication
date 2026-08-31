---
description: deploy ui only
---

```bash
export REGISTRY=hyd.ocir.io/axekmbadoczl
Deployment/ship.sh food-delivery-app-ui
```

Same command as any backend service. `ship.sh` runs `npm ci && npm run build`, publishes the image
to OCIR, deploys it, and verifies the container ended up on the intended image. Nothing is rsynced
and nothing is built on the VM.

Then **hard-refresh the browser** (`Cmd+Shift+R`). The SPA caches `index.html`, so a correct deploy
looks like nothing happened until you do.

## Two things specific to this service

- Its Docker build context is its **own** directory, not the workspace root, because its Dockerfile
  does `COPY nginx.conf`. Recorded in `Deployment/service-map.tsv`.
- `~/.npm/_cacache` on this Mac contains root-owned files left by an old `sudo npm` run, which makes
  `npm ci` delete `node_modules` and then fail. `ship.sh` detects this and falls back to a private
  cache. The permanent fix, which needs your password:

  ```bash
  sudo chown -R $(whoami) ~/.npm
  ```

`--no-cache` is no longer needed: images are identified by a git-sha tag, so a stale `COPY dist`
layer cannot be silently reused.
