# Release Workflows

Two wrapper workflows call `_release_common.yml`: one for standard releases (from `develop`) and one for hotfixes (from `hotfix/*`). Do **not** trigger `_release_common.yml` directly.

## Standard Release (from develop)
1. Go to Actions → **Release - Standard**.
2. Ensure the run is on the `develop` branch.
3. Enter `release-version` (X.Y.Z). Optional: `next-snapshot-version` (X.Y.Z or X.Y.Z-SNAPSHOT). If blank, minor auto-bumps.
4. Run the workflow.

What happens:
- Validations: branch must be `develop`; `release-version`/`next-snapshot-version` format checked; parent POM and dependencies must not be SNAPSHOT; tag/package version must not already exist.
- Creates and pushes `release/X.Y.Z` branch (standard only).
- Builds, tests, runs OWASP dependency-check, deploys artifacts to GitHub Packages.
- Publishes site to GitHub Pages (`current/` and `vX.Y.Z/`).
- Updates `RELEASE_NOTES.md`, merges to `main` with `-X theirs`, tags `vX.Y.Z`.
- Merges back to `develop` with `-X ours` and bumps to next SNAPSHOT.
- Creates GitHub Release.

## Hotfix Release (from hotfix/*)
1. Create `hotfix/X.Y.Z` from `main`, land fixes via PRs into that branch.
2. Go to Actions → **Release - Hotfix**.
3. Ensure the run is on your `hotfix/*` branch.
4. Enter `release-version` (X.Y.Z) and run.

What happens:
- Validations: branch must be `hotfix/*`; `release-version` format checked; parent POM/dependencies must not be SNAPSHOT; tag/package version must not already exist.
- Uses existing `hotfix/*` branch (no new branch created).
- Builds/tests/deploys, runs OWASP check, publishes site, updates `RELEASE_NOTES.md`.
- Merges to `main` with `-X theirs`, tags `vX.Y.Z`.
- Merges back to `develop` with `-X ours` (no version bump). Warns about any hotfix files that may need manual cherry-pick (`UNMERGED_HOTFIX_FILES`).

## Outputs and artifacts
- Git tag `vX.Y.Z` on `main`.
- GitHub Release with generated notes.
- `RELEASE_NOTES.md` updated.
- GitHub Pages site published to `current/` and `vX.Y.Z/`.
- Dependency check report uploaded as an artifact.
- Release branch is kept after completion.

## If Something Goes Wrong
The workflow attempts cleanup on failure (delete tag/release/package, revert main merge, delete release branch for standard runs). The job summary lists any manual steps still needed.

## Full guide
See [RELEASING.md](../../RELEASING.md) for the concise runbook.
