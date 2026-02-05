# Releasing v2tofhir

Two workflows wrap the shared `_release_common.yml`: **Release - Standard** (from `develop`) and **Release - Hotfix** (from `hotfix/*`). Do **not** run `_release_common.yml` directly.

## Standard release (from develop)
1. Go to Actions → **Release - Standard**.
2. Ensure the run is on `develop`.
3. Enter `release-version` (X.Y.Z). Optional: `next-snapshot-version` (X.Y.Z or X.Y.Z-SNAPSHOT). If blank, the minor version auto-bumps to the next SNAPSHOT.
4. Run the workflow.

Key behaviors:
- Validates branch, version formats, no SNAPSHOT parent/deps, and that the tag/package version does not already exist.
- Creates and pushes `release/X.Y.Z` branch.
- Builds/tests, deploys to GitHub Packages, runs OWASP dependency check.
- Publishes site to GitHub Pages (`current/` and `vX.Y.Z/`).
- Updates `RELEASE_NOTES.md`, merges to `main` with `-X theirs`, tags `vX.Y.Z`.
- Merges back to `develop` with `-X ours`.
- For standard releases, bumps `develop` to the next SNAPSHOT version (hotfix releases do not bump the version).

## Hotfix release (from hotfix/*)
1. Create `hotfix/X.Y.Z` from `main` and merge fixes into that branch.
2. Go to Actions → **Release - Hotfix**.
3. Ensure the run is on your `hotfix/*` branch.
4. Enter `release-version` (X.Y.Z) and run.

Key behaviors:
- Validates branch, version format, no SNAPSHOT parent/deps, and that the tag/package version does not already exist.
- Uses the existing `hotfix/*` branch (no new branch created).
- Builds/tests, deploys, runs OWASP check, publishes site, updates `RELEASE_NOTES.md`.
- Merges to `main` with `-X theirs`, tags `vX.Y.Z`.
- Merges back to `develop` with `-X ours` (no version bump). If any hotfix files may need manual cherry-pick, the summary lists them (`UNMERGED_HOTFIX_FILES`).

## Outputs and artifacts
- Git tag `vX.Y.Z` on `main` and a GitHub Release with generated notes.
- `RELEASE_NOTES.md` updated.
- GitHub Pages site published to `current/` and `vX.Y.Z/`.
- Dependency check report uploaded as an artifact.
- Release branch is kept after completion.

## Failures
On failure, the workflow attempts to clean up (tag/release/package deletion, revert main merge, delete release branch for standard runs). Check the job summary for any manual steps that remain.
