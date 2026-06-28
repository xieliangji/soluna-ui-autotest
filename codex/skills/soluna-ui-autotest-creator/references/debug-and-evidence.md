# 调试和证据

case、locator、visual template、OCR 断言或状态 fragment 需要真实设备调试时读取本文件。

## 调试 Shell

优先使用一个长生命周期 shell：

```bash
soluna debug <plan.yaml> shell
```

常用 shell 命令：

```text
restart-app
source --out build/soluna-debug/<step>.xml
screenshot --out build/soluna-debug/<step>.png
tap --x-ratio 0.50 --y-ratio 0.50
tap-element --strategy xpath --locator "<locator>"
longPress --x-ratio 0.50 --y-ratio 0.30 --duration-ms 1200
longPress-element --strategy xpath --locator "<locator>" --element-x-ratio 0.50 --element-y-ratio 0.50 --duration-ms 1200
swipe --start-x-ratio 0.50 --start-y-ratio 0.80 --end-x-ratio 0.50 --end-y-ratio 0.25 --duration-ms 500
swipe-element --strategy xpath --locator "<locator>" --start-x-ratio 0.50 --start-y-ratio 0.90 --end-x-ratio 0.50 --end-y-ratio 0.10
input --strategy class --locator "<class>" --text "text" --clear-first true
tap-template --template <png> --roi x,y,w,h --threshold 0.72 --scales 0.8,1.0,1.2
```

只有重复 one-shot debug 命令确实有价值时，才使用 `--keep-infra`。

## 证据规则

- 从真实 app restart 后的状态开始调试，除非场景依赖脏状态。
- 信任 locator 前先抓 source 和 screenshot。
- 页面导航、restart、键盘收起、弹窗变化、WebView transition、template tap 后，重新抓 source 和 screenshot。
- swipe 后重新抓 source 和 screenshot，再判断新出现的元素。
- UI transition 后不要复用旧 XML。
- iOS WebView 已知存在 hidden stale nodes 时，短暂等待后再抓 source。
- `tap-template` 证据要记录 match score、bounds、ROI、threshold、scales 和截图。
- OCR 证据要记录 recording resource id、ROI、candidate strategy、recognizer，以及可用时的 matched frame/resource。

## 从调试结果转换为 DSL

- debug `tap-element` / `longPress-element` locator 必须先进入 element catalog，再用于 case。
- debug `tap-template` path 必须先进入 data file parameter，再用于 case。
- raw viewport tap 只允许用于没有稳定元素或模板的表面，例如 modal backdrop。
- wait 只加在 UI 行为确实需要的位置。不要用长全局 sleep 替代状态断言。
- toast-like transient assertion 应使用 screen recording analysis，不用 page source polling。
