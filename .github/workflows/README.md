# Release Workflows

Two ways to release: standard and hotfix.

## Standard Release

For normal releases from `develop`.

1. Go to Actions > "Release - Standard"
2. Make sure you're on the `develop` branch
3. Enter the release version (e.g., `2.4.0`)
4. Optionally set the next SNAPSHOT version (defaults to bumping to the minor version)
5. Hit run

What happens:
- Creates a `release/X.Y.Z` branch
- Runs tests, OWASP check, deploys to GitHub Packages
- Merges to `main`, tags it
- Bumps `develop` to the next SNAPSHOT version
- Creates a GitHub Release

## Hotfix Release

For urgent fixes that can't wait for a normal release cycle.

1. Create your hotfix branch from `main`:
   ```bash
   git checkout main
   git pull
   git checkout -b hotfix/2.14.1
   git push -u origin hotfix/2.14.1
   ```
2. Make your fixes (via PRs to the hotfix branch)
3. Go to Actions > "Release - Hotfix"
4. Make sure you're on your `hotfix/*` branch
5. Enter the release version
6. Hit run

What happens:
- Same as standard release, but doesn't bump `develop`'s version
- Merges release notes to `develop` though

## If Something Goes Wrong

The workflow tries to clean up after itself on failure (deletes tags, reverts merges, removes deployed artifacts, etc.). Check the job summary for any manual cleanup steps if needed.

## The Boring Details

Both workflows call `_release_common.yml` under the hood. Don't run that one directly.

For the full release guide, see [RELEASING.md](../../RELEASING.md).
