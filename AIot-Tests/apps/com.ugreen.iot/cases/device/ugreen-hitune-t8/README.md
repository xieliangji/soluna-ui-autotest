# UGREEN HiTune T8 Device Cases

This directory is for device cases that are specific to `UGREEN HiTune T8`.

Keep cross-model device-list cases in `../common/`. Add cases here only when
the scenario depends on this model's capabilities, pages, data, logs, or
behavior.

Feature coverage should be modeled as cross-platform by default: if a T8
feature is available on iOS, assume the Android app should expose the same
feature. Formal T8 feature case IDs and file names should not carry `_IOS` or
other platform suffixes. Keep platform differences in plans, locators, data, or
log assertion arguments. Probe-only `TC000_*_PROBE_IOS` cases may keep the suffix
because they document temporary iOS exploration, not formal feature coverage.

T8 iOS detail entry can show a firmware-upgrade prompt. Model-specific cases
that open the detail page should use the model-specific optional ignore-button
tap with `ignoreMissingElementReason: optionalFirmwareUpgradePrompt` instead of
adding coordinate or title-area dismissal steps.

The formal iOS plan at `plans/device/ugreen-hitune-t8/ios.yaml` currently runs
TC002-TC017 through the T8-specific logged-in device-page fragment. TC001 remains
outside that formal plan until the remaining noise-control interactions are
converted from viewport-position taps to stable element-relative targets.
T8 cases should use `data/device/ugreen-hitune-t8.yaml` for model data and must
not reference common mine language data; those fixtures are for common app/device
cases and are split by UI language, for example `data/common/mine.zh-CN.yaml`.

Bluetooth-interaction cases under this directory may include log capture and
assertions around actions such as noise-control or music-mode changes. Do not
wrap generic navigation, firmware prompt dismissal, or unrelated UI operations
with app-log assertions.
For iOS BLE captures, assert only that the interaction triggered a
CoreBluetooth characteristic write. Do not model this as a headset ACK/report
assertion unless a lower-level capture or App protocol log exposes that data.
Android/SPP interaction assertions should be explored separately and may assert
full downstream/upstream logs once that path is formalized.

The T8 find-route map page renders an earbuds icon after the earbud location is
resolved. The route-map case first asserts the native map element, then saves an
explicit screenshot and uses `assertImageColorRatio` with `color: green` in a
focused ROI around the earbuds icon to confirm the earbud location marker is
visible. Revisit OCR road-label checks only after the map zoom/viewport
reliably renders road-name text.

When a T8 detail control can be resolved as an element, prefer element
screenshots for visual state assertions so color checks read the precise control
image. Use bounded viewport coordinates only as a focused debug fallback when no
stable element or accessibility target is available.

For switch controls such as game mode and device dual connect, use the switch
element screenshot and `assertImageColorRatio` on the green indicator instead of
asserting the iOS `value` attribute. The native attribute can be less stable in
continuous runs even when the visual switch state has already changed.

For custom equalizer cleanup, reopen the saved custom entry after BLE-log
assertion, save an element screenshot of the equalizer-name row, save that row
rect as an ROI, then click the delete affordance with `tapVisualTemplate` inside
the ROI. Keep this as template-based visual interaction because the delete icon
is not exposed as a stable named element.

For the current validated detail page, the find-earbuds card is opened by
tapping the bottom card center after scrolling the detail page. This avoids
relying on the localized card title as the interaction locator while keeping the
route-map assertions on native map presence and named-color screenshot analysis.

The T8 more page is opened from the detail page by tapping the top-right
settings icon. On iOS the icon is exposed as a visible accessible
`XCUIElementTypeOther` at the right side of the title bar, without a stable text
label. The more page rows are exposed as accessible row nodes with combined
labels such as `设备名称, UGREEN HiTune T8`, `提示音, 中文`, `使用说明书`,
`自定义控制`, `固件升级, v1.4.6`, `恢复出厂设置`, and `删除设备`; the
disconnect action is exposed as a button named `断开连接`.

For the T8 more-page disconnect/reconnect path, confirming disconnect does not
automatically navigate back to the device detail page. Tap back first, then use
the detail page `连接` button to restore the connection. A firmware-upgrade
prompt can appear before reconnecting; add a separate 5-second optional tap on
the language-insensitive firmware prompt structure in this reconnect path
instead of relying on the initial `device.openTargetDevice` fragment dismissal.

For T8 more-page rename debugging, the first tap on `设备名称` can open the edit
panel with the iOS system keyboard covering the lower part of the panel. Prime
the flow by opening the edit panel once, then tap the top title area twice to
dismiss the keyboard and close the panel before reopening the edit panel for the
real rename operation. The edit input is not exposed as a native
`XCUIElementTypeTextField`; locate it relative to the stable left-top close
icon in the dialog instead of absolute screen coordinates. The input action does
not clear this React Native input reliably by itself; clear through the input
field's right-side clear affordance first, then type the target name with
`clearFirst: false`. The clear affordance can be clicked as an element-relative
right-side tap inside the input container.

For T8 custom control under the more page, the entry row is `自定义控制`. The
custom control page title is also `自定义控制`; it exposes `左耳` and `右耳` tabs
and accessible action rows such as `单击, 播放/暂停`, `双击, 上一曲`, `三击, 语音助手`,
and `长按, 降噪关/环境音/降噪开`. Tapping a gesture row opens a bottom option
sheet. Verified option coverage includes single tap (`无`, `播放/暂停`,
`调大音量`, `调小音量`), triple tap (`无`, `语音助手`, `游戏模式`, `空间音效`,
`AI录音`), and long press (`无`, `语音助手`, `降噪关/环境音/降噪开`, `AI录音`).
The option sheet has a stable left-top close icon; option locators should anchor
to that sheet structure and then select options by element order inside the
sheet. Selecting an option closes the sheet and updates the row text. Formal
automation is split by gesture (`TC014` double tap, `TC015` single tap, `TC016`
triple tap, and `TC017` long press), covers both ears, normalizes each ear to
the baseline option before switching to the target option, then restores the
baseline option. Assertions are limited to UI row state and BLE write logs;
physical earbud tap behavior is outside normal UI automation unless hardware
actions or lower-level logs are added.

Debug evidence:

- `t8-find-route-debug-20260622-color-006` passed on iOS real device for
  `TC002_FIND_ROUTE`; report:
  `build/soluna-runs/t8-find-route-debug-20260622-color-006/report/index.html`;
  upload completed with `uploaded=4, failed=0, abandoned=0`.
- `t8-find-route-debug-20260623-earbud-icon-002` passed after narrowing the
  color assertion to the earbuds-icon ROI; the assertion observed green ratio
  `0.30117481803090285` with `9434/31324` pixels; report:
  `build/soluna-runs/t8-find-route-debug-20260623-earbud-icon-002/report/index.html`;
  upload completed with `uploaded=4, failed=0, abandoned=0`.
- `t8-music-mode-step4-element-screenshot-20260626-001` passed after changing
  the low-bass music-mode selected-state check to save the `低音增强` element
  screenshot; the element image was `312x258`, and the green assertion observed
  ratio `0.03152951699463327` with `2538/80496` pixels; report:
  `build/soluna-runs/t8-music-mode-step4-element-screenshot-20260626-001/report/index.html`;
  upload completed with `uploaded=4, failed=0, abandoned=0`.
- `t8-game-mode-debug-20260626-005` passed after changing game-mode state
  evidence from switch `value` to switch element screenshot green coverage;
  report:
  `build/soluna-runs/t8-game-mode-debug-20260626-005/report/index.html`;
  upload completed with `uploaded=5, failed=0, abandoned=0`.
- `t8-dual-connect-debug-20260626-003` passed after applying the same switch
  element screenshot state evidence to device dual connect; report:
  `build/soluna-runs/t8-dual-connect-debug-20260626-003/report/index.html`;
  upload completed with `uploaded=5, failed=0, abandoned=0`.
- `t8-custom-equalizer-debug-20260626-008` passed after resetting the custom
  equalizer before log capture, asserting iOS BLE characteristic write after the
  save action, and deleting the custom entry through equalizer-name-row
  screenshot ROI plus delete-icon template matching; report:
  `build/soluna-runs/t8-custom-equalizer-debug-20260626-008/report/index.html`;
  upload completed with `uploaded=5, failed=0, abandoned=0`.
- `t8-ios-full-20260626-002` passed the formal T8 iOS plan with 7 cases:
  noise control, find route, music mode, spatial audio, game mode, equalizer
  preset, and dual connect; report:
  `build/soluna-runs/t8-ios-full-20260626-002/report/index.html`; upload
  completed with `uploaded=15, failed=0, abandoned=0`.
- `t8-more` step-by-step debug on 2026-06-26 verified the more-page structure
  and the 2-character rename path. Evidence under `build/soluna-debug/`:
  `t8-more-step02-more-page.*` captured the more page; `step03` reproduced the
  first-open keyboard overlap; `step08` confirmed clean input of `T8`; `step09`
  confirmed the more-page name changed to `T8`; `step10` confirmed the detail
  title changed to `T8`; `step13` confirmed restore to `UGREEN HiTune T8`.
  The formal YAML case now includes the 2-character, 30-character, cancel, and
  restore paths.
- `t8-custom-control` step-by-step debug on 2026-06-26 verified the more-page
  custom control path. Evidence under `build/soluna-debug/`:
  `t8-custom-control-step01-page.*` captured the custom control page; `step02`
  captured the left-ear double-tap option sheet; `step03` confirmed left-ear
  `双击` changed from `上一曲` to `下一曲`; `step05` confirmed the same switch on
  right ear; `step07` and `step08` confirmed left and right ears were restored
  to `双击, 上一曲`.
- `t8-custom-control-debug-20260626-005` passed the focused automation plan with
  `TC014` double tap, `TC015` single tap, `TC016` triple tap, and `TC017` long
  press. The cases normalize current left/right ear settings before switching
  options, which allowed rerun after a previous interrupted triple-tap run left
  the left ear at `三击, 游戏模式`; report:
  `build/soluna-runs/t8-custom-control-debug-20260626-005/report/index.html`;
  upload completed with `uploaded=7, failed=0, abandoned=0`.
- `t8-custom-control-debug-20260626-002` passed the initial focused double-tap
  automation case after the common firmware-upgrade prompt locator was aligned
  with the iOS firmware prompt structure and optional tap also skipped explicit
  wait timeouts;
  report:
  `build/soluna-runs/t8-custom-control-debug-20260626-002/report/index.html`;
  upload completed with `uploaded=4, failed=0, abandoned=0`.
- `t8-ios-tc009-redo-20260627-003` passed the focused rename case after
  removing invalid coordinate/text locator patterns and using element-relative
  taps for the React Native input clear affordance; report:
  `build/soluna-runs/t8-ios-tc009-redo-20260627-003/report/index.html`; upload
  completed successfully.
- `t8-ios-tc010-tc017-redo-20260627-002` passed the focused more-page batch with
  `TC010` through `TC017`: prompt sound UI plus BLE write logging, manual
  screenshot OCR keyword validation, delete cancel only, disconnect/back/detail
  reconnect, and all custom-control gesture cases. Report:
  `build/soluna-runs/t8-ios-tc010-tc017-redo-20260627-002/report/index.html`;
  upload completed with `uploaded=9, failed=0, abandoned=0`.

Deferred coverage:

- Anti-wind-noise is not added yet because the current source and screenshots do
  not provide a stable visible entry/locator for formal automation on T8.
- Audio sharing is not added because the current T8 model build does not expose
  that function.

Model-specific locators should be added under `../../../elements/device/` when
T8 detail-page controls expose stable, model-only elements. Keep public app and
device-list locators in `../../../elements/common.yaml`.
