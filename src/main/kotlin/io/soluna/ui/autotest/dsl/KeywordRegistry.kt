package io.soluna.ui.autotest.dsl

interface KeywordRegistry {
    fun normalize(rawKeyword: String): String?
}

class MapKeywordRegistry(
    aliases: Map<String, Set<String>>,
) : KeywordRegistry {
    private val normalizedByAlias: Map<String, String> =
        aliases.flatMap { (canonical, values) ->
            (values + canonical).map { alias -> alias.trim().lowercase() to canonical }
        }.toMap()

    override fun normalize(rawKeyword: String): String? {
        return normalizedByAlias[rawKeyword.trim().lowercase()]
    }
}

object DefaultKeywordRegistry : KeywordRegistry {
    private val delegate = MapKeywordRegistry(
        mapOf(
            "tap" to setOf("click", "点击", "轻点"),
            "tapPosition" to setOf("tapAt", "positionTap", "点击位置", "按位置点击", "坐标点击"),
            "longPress" to setOf("longTap", "pressAndHold", "长按", "长按点击"),
            "swipe" to setOf("滑动", "划动"),
            "tapVisualTemplate" to setOf("tapImage", "tapTemplate", "视觉点击", "模板点击", "图片点击"),
            "assertImageColorRatio" to setOf(
                "imageColorRatio",
                "assertImageContainsColor",
                "imageContainsColor",
                "断言图片颜色占比",
                "图片颜色占比",
                "图片含颜色量",
                "图片含蓝色量",
            ),
            "input" to setOf("type", "输入", "录入"),
            "restartApp" to setOf("restart", "重启应用", "重启App", "重启APP"),
            "clearAppData" to setOf("clearApplicationData", "清除应用数据", "清理应用数据"),
            "getText" to setOf("readText", "saveText", "获取文本", "读取文本", "保存文本"),
            "saveElementRect" to setOf("getElementRect", "saveElementRegion", "获取元素矩形", "保存元素矩形", "保存元素区域"),
            "wait" to setOf("sleep", "pause", "等待", "暂停"),
            "assertElementExists" to setOf(
                "elementExists",
                "assertElementPresent",
                "elementPresent",
                "断言元素存在",
                "元素存在",
            ),
            "assertElementAttrEquals" to setOf(
                "elementAttrEquals",
                "attrEquals",
                "断言元素属性相等",
                "元素属性相等",
                "属性相等",
            ),
            "assertElementAttrRegexMatch" to setOf(
                "elementAttrRegexMatch",
                "attrRegexMatch",
                "断言元素属性匹配",
                "元素属性匹配",
                "属性匹配",
            ),
            "assertSourceRegexMatch" to setOf(
                "sourceRegexMatch",
                "断言源码匹配",
                "源码匹配",
            ),
            "screenshot" to setOf("截图", "显式截图"),
            "startScreenRecording" to setOf("startRecording", "开始录屏", "开始屏幕录制"),
            "stopScreenRecording" to setOf("stopRecording", "停止录屏", "停止屏幕录制"),
            "assertScreenRecordingTextRegexMatch" to setOf(
                "screenRecordingTextRegexMatch",
                "断言录屏文本匹配",
                "录屏文本匹配",
            ),
            "assertImageTextRegexMatch" to setOf(
                "imageTextRegexMatch",
                "assertScreenshotTextRegexMatch",
                "screenshotTextRegexMatch",
                "断言图片文本匹配",
                "图片文本匹配",
                "断言截图文本匹配",
                "截图文本匹配",
            ),
            "captureAppLogStart" to setOf("startAppLogCapture", "开始采集App日志", "开始抓取App日志"),
            "captureAppLogEnd" to setOf("stopAppLogCapture", "结束采集App日志", "结束抓取App日志"),
            "customAssertAppLog" to setOf("assertCustomAppLog", "自定义断言App日志", "自定义App日志断言"),
        ),
    )

    override fun normalize(rawKeyword: String): String? {
        return delegate.normalize(rawKeyword)
    }
}
