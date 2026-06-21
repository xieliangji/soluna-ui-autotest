# Distribution Workflow

Use this reference when working with a packaged Soluna distribution or when verifying asset projects through the packaged CLI.

## CLI Boundary

Prefer the packaged executable:

```bash
soluna validate <plan.yaml>
soluna run <plan.yaml> --run-id <run-id>
soluna debug <plan.yaml> shell
```

Use the executable from the active Soluna distribution. Do not make an external asset project depend on source-tree paths.

If `validate` is not present in the current distribution, use the narrowest non-destructive `run` command available and record that validation was unavailable.

## Distribution Contents

The distribution should include:

- `bin/soluna`: CLI entry point.
- `tools/`: bundled runtime tools such as FFmpeg.
- `plugins/soluna-appium-ext/`: bundled Appium extension source.
- `codex/skills/soluna-ui-autotest-creator/`: this Codex skill and scaffolding resources.

Asset projects should not depend on framework source paths. Reference only files inside the asset project and execute through the distribution CLI.

## Verification Order

1. Validate YAML contracts and reference resolution.
2. Run focused debug plans with `failureStrategy: stop-case`.
3. Run broader plans only after focused plans pass.
4. Inspect report JSON, HTML, trace diagnostics, and `plan-resource-manifest.json` when execution fails or resources are part of the assertion.

Use stable run ids for debugging so reports and MinIO paths are easy to correlate.
