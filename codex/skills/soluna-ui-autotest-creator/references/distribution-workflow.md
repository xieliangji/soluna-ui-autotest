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
- `plugins/app-log/`: optional runtime location for independent app-log assertion plugin JARs used by `customAssertAppLog`.
- `codex/skills/soluna-ui-autotest-creator/`: this Codex skill and scaffolding resources.

Asset projects should not depend on framework source paths. Reference only files inside the asset project and execute through the distribution CLI.

App-specific log parsers and matchers should be built as independent JVM plugin JARs. Place the JAR and any sibling dependency JARs in the active distribution's `plugins/app-log/`, or pass additional directories with `-Dsoluna.appLogPluginDirs=<paths>` / `SOLUNA_APP_LOG_PLUGIN_DIRS=<paths>`. Do not write parser code inside case, data, element, or fragment assets.

Create a starter app-log assertion plugin project through the Soluna CLI:

```bash
soluna scaffold app-log-plugin ./ugreen-audio-log-plugin \
  --plugin-id ugreen-audio \
  --package com.ugreen.soluna.applog \
  --assertion ble-command-ack
```

Build the generated project with `SOLUNA_HOME=/path/to/soluna gradle test jar` or `gradle test jar -PsolunaHome=/path/to/soluna`, then copy the resulting JAR to `plugins/app-log/`.

## Verification Order

1. Validate YAML contracts and reference resolution.
2. Run focused debug plans with `failureStrategy: stop-case`.
3. Run broader plans only after focused plans pass.
4. Inspect report JSON, HTML, trace diagnostics, and `plan-resource-manifest.json` when execution fails or resources are part of the assertion.

In `execution-result.json`, start with `summary` for stage/case/action totals, then `failures` for the flattened failed action locations. Use action metadata such as `actionId`, `actionKeyword`, `attempt`, and `durationMs` to correlate report rows with case YAML. Use `traceArtifacts` for failed-action screenshot/page-source evidence, and use `plan-resource-manifest.json` for explicit screenshot, screen-recording, and OCR evidence resources.

Use the HTML report as debugging evidence, not as an authoring surface. Start from the case overview and failure summary, then open the related case action details only when action-level evidence is needed. `productModel` is the plan display heading for run outputs; installed app names and device display names should come from Soluna/Appium extension metadata when available instead of being hardcoded for report or notification display.

Use stable run ids for debugging so reports and MinIO paths are easy to correlate.
