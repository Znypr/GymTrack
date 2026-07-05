# Work-item automation

The `Create branch and draft pull request` workflow runs when an issue receives `status:ready`.

## Authentication order

The workflow uses the first available credential:

1. `WORK_ITEM_TOKEN`;
2. `GYMTRACK_PROJECT_TOKEN`;
3. the repository `GITHUB_TOKEN`.

GymTrack already uses `GYMTRACK_PROJECT_TOKEN` for the user-owned GitHub Project. When that token has repository write access, it can also create work-item branches, pull requests, and issue comments.

A separate `WORK_ITEM_TOKEN` remains supported when least-privilege separation is preferred. It needs Contents, Issues, and Pull requests write access.

## Repository-token fallback

When neither repository secret is available, open **Settings → Actions → General → Workflow permissions** and configure:

- **Read and write permissions**;
- **Allow GitHub Actions to create and approve pull requests**.

The workflow does not approve pull requests. GitHub uses the second setting to permit pull-request creation with `GITHUB_TOKEN`.

## Manual recovery

When the label event is missed or fails:

1. Open **Actions**.
2. Select **Create branch and draft pull request**.
3. Select **Run workflow**.
4. Enter the issue number.

The issue must have `status:ready`. The workflow checks existing branches and pull requests before creating anything, so rerunning it is safe.

## Expected output

A successful run creates:

- a typed branch such as `fix/129-example-title`;
- an initial empty commit linked to the issue;
- a draft pull request against the default branch;
- an issue comment linking the branch and pull request.

A forbidden pull-request response reports which token or repository setting must be configured rather than failing with an unexplained API error.
