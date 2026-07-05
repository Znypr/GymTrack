# Work-item automation

The `Create branch and draft pull request` workflow runs when an issue receives `status:ready`.

## Required repository settings

Open **Settings → Actions → General → Workflow permissions** and configure:

- **Read and write permissions**;
- **Allow GitHub Actions to create and approve pull requests**.

The workflow does not approve pull requests. GitHub uses the second setting to permit pull-request creation with `GITHUB_TOKEN`.

When repository policy prevents this setting, create a fine-grained token with Contents, Issues, and Pull requests write access and save it as the Actions secret `WORK_ITEM_TOKEN`.

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
