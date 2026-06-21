# Debug And Evidence

Use this reference when a case, locator, visual template, OCR assertion, or state fragment needs real-device debugging.

## Debug Shell

Prefer one long-lived shell:

```bash
soluna debug <plan.yaml> shell
```

Useful shell commands:

```text
restart-app
source --out build/soluna-debug/<step>.xml
screenshot --out build/soluna-debug/<step>.png
tap --x-ratio 0.50 --y-ratio 0.50
tap-element --strategy xpath --locator "<locator>"
input --strategy class --locator "<class>" --text "text" --clear-first true
tap-template --template <png> --roi x,y,w,h --threshold 0.72 --scales 0.8,1.0,1.2
```

Use `--keep-infra` only when explicitly useful for repeated one-shot debug commands.

## Evidence Rules

- Start state debugging from a real app restart.
- Capture source and screenshot before trusting a locator.
- Capture fresh source and screenshot after page navigation, restart, keyboard dismissal, modal changes, WebView transitions, and template taps.
- Do not reuse old XML after UI transitions.
- For iOS WebView, wait briefly before source capture when stale hidden nodes are known to appear.
- For `tap-template`, record match score, bounds, ROI, threshold, scales, and screenshot.
- For OCR, record recording resource id, ROI, candidate strategy, recognizer, and matched frame/resource when available.

## Converting Debug Into DSL

- Debug `tap-element` locators must become element catalog entries before use in cases.
- Debug `tap-template` paths must become data-file parameters before use in cases.
- Raw viewport taps are allowed only for surfaces with no stable element or template, such as modal backdrops.
- Add waits only where the UI behavior needs them. Do not use long global waits as a substitute for better state assertions.
- Toast-like transient assertions should use screen recording analysis, not source polling.
