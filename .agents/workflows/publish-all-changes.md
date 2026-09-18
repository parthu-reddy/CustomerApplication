---
description: Commit, push and verify changes across all repos via GitHub Actions. Three steps, run in order, each independently re-runnable.
---

# Publish all changes

Three steps, all through GitHub Actions. Run them in order, check each before the next. They are
separate on purpose: when something fails you re-run one step, not a 200-line script from the top.

Nothing here builds or tests locally. Verification happens in CI: `build-and-push` runs the unit
tests, `publish-stubs` now runs each service's producer contract tests before publishing its stubs,
and `contract-tests` runs the consumer side. `build_verify.sh` exists for local debugging and is
deliberately NOT part of this workflow.

The scripts live in `FoodDeliveryContracts/ci/`, next to `repo-map.tsv` and `build_verify.sh`.
All of them take `--dry-run`.

## 1. Commit and push

```bash
bash FoodDeliveryContracts/ci/commit_and_push.sh -m "what actually changed" --dry-run
bash FoodDeliveryContracts/ci/commit_and_push.sh -m "what actually changed"
```

Shows every file it will commit, per repo, and asks once. A failed rebase is fatal for that repo and
nothing is pushed there. `-m` is required: the history is already a wall of
"chore: automated workflow update" because the previous version used one fixed message everywhere.

## 1.5. Enable Actions (Visibility)

```bash
bash FoodDeliveryContracts/ci/flip_visibility.sh --public
```

Because of billing limits, repositories must temporarily be made public to run GitHub Actions, except for unsafe repos (which will be skipped).

## 2. Build (GitHub Actions)

```bash
bash FoodDeliveryContracts/ci/build_services.sh --dry-run
bash FoodDeliveryContracts/ci/build_services.sh
```

Base dependencies first and one at a time — `FoodDeliveryParent`, `IdentitySigning`, `CommonLibrary`
each publish to GitHub Packages what the next resolves — then every service together. Waits for each
group and stops on the first failure.

## 3. Contracts (GitHub Actions)

```bash
bash FoodDeliveryContracts/ci/orchestrate_contracts.sh --phase 1
# stop and diagnose if anything failed
bash FoodDeliveryContracts/ci/orchestrate_contracts.sh --phase 2
```

Two phases, not an order: five producer/consumer pairs are mutual, so no sequence exists where every
consumer reads current stubs. See `ci/CONTRACT_CI_RUNBOOK.md`.

## 3.5. Revert Visibility

```bash
bash FoodDeliveryContracts/ci/flip_visibility.sh --revert
```

Run this to restore repositories back to private. It remembers their original state and only reverts the ones it made public.

## Deploy — manual, separate, not part of this workflow

**Not automated, and not chained to the steps above.**

```bash
cd Deployment && ./OracleDeployment/03_clean_deploy.sh --wipe
cd Deployment && ./dummy-data.sh
```

Answer their prompts yourself. `--wipe` destroys every container and every volume on the VM;
`dummy-data.sh` drops the public schema of twelve databases. Both scripts ask before doing it.

The previous version of this workflow ran them unattended as
`echo "WIPE" | ./03_clean_deploy.sh --wipe` and `./dummy-data.sh --yes`, piping past both
confirmations. Those prompts were written by someone who meant them. Do not automate past them.

Note deployment is parked as of 2026-09-13 — nothing is in production — so most of the time this
step should simply not run.

## What the rewrite removed, and why

- **Repository visibility flipping.** The old script made every modified repo public to run Actions,
  then a `trap cleanup EXIT` forced them all private — unconditionally, including on failure, and
  without recording what they were. Visibility is currently MIXED (`CustomerApplication` public,
  `CommonLibrary` private), so that trap would have silently privatised repos it did not own. Public
  exposure is not theoretical here: that is how the committed JWT private key was exposed on
  2026-09-13. If saving Actions minutes matters, make that a deliberate decision, not a side effect
  of a deploy script.
- **`git add .` with a fixed message in every repo.** Commits whatever happens to be lying around.
- **`git pull --rebase origin main || true` followed by `git push`.** A conflicted rebase was
  swallowed and the push went out from a half-rebased tree.
- **`gh run list -L N` to find the runs it just started.** Unfiltered — the N most recent runs in the
  repo. Any unrelated run in flight and it watched the wrong one. Both replacements filter by
  workflow name and start time.
- **A hardcoded service list** that had already drifted from `repo-map.tsv`, and a `DEPENDENCIES`
  entry of `FoodDeliveryParentPOM` — the repo name, where the directory is `FoodDeliveryParent`, so
  `cd` failed and the parent POM never built at all.

## For agents running this

- Run one step, read its output, then decide. Do not chain them unattended.
- Everything runs in GitHub Actions. Do NOT start `build_verify.sh` as part of this workflow — it is
  a 15-25 minute local build, it runs offline against a warm `~/.m2`, and a green result is not
  evidence that CI will pass. Use it only when separately debugging a local failure.
- These scripts block while they wait. If you background one, you are not watching it — say so
  rather than claiming you will monitor and notify.
- `mvn test -Dtest=X` without `clean` exits 1 with "No tests matching pattern" for generated contract
  tests. That means "ran nothing", not "found a bug".
- Local maven defaults to offline against a warm `~/.m2`. Local green is not evidence for a CI change.
- Never commit or push unless asked in the moment.
