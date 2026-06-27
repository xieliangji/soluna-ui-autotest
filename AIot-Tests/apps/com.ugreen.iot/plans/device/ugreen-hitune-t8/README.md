# UGREEN HiTune T8 Plans

Formal run plans for `UGREEN HiTune T8` live here.

- `ios.yaml`: iOS real-device regression for the validated T8 cases. It covers
  noise control, find route, music mode, spatial audio, game mode, equalizer
  preset, dual connect, custom equalizer, and the more-page rename, prompt
  sound, user manual OCR, delete-cancel, disconnect/reconnect, and custom
  control paths. Latest full-plan verified run before adding custom equalizer
  and more-page cases:
  `t8-ios-full-20260626-002`, report
  `build/soluna-runs/t8-ios-full-20260626-002/report/index.html`, status
  passed, uploads `uploaded=15, failed=0, abandoned=0`.
- Custom equalizer focused verification:
  `t8-custom-equalizer-debug-20260626-008`, report
  `build/soluna-runs/t8-custom-equalizer-debug-20260626-008/report/index.html`,
  status passed, uploads `uploaded=5, failed=0, abandoned=0`.
- Custom control focused verification:
  `t8-custom-control-debug-20260626-005`, report
  `build/soluna-runs/t8-custom-control-debug-20260626-005/report/index.html`,
  status passed for `TC014` double tap, `TC015` single tap, `TC016` triple tap,
  and `TC017` long press; uploads `uploaded=7, failed=0, abandoned=0`.

Focused or temporary debug plans belong in `../../debug/`. Public app-wide and
cross-model plans belong in `../../common/`.
