package com.ugreen.iot.soluna.autotest.dsl

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
            "tapVisualTemplate" to setOf("tapImage", "tapTemplate", "视觉点击", "模板点击", "图片点击"),
            "input" to setOf("type", "输入", "录入"),
            "restartApp" to setOf("restart", "重启应用", "重启App", "重启APP"),
            "getText" to setOf("readText", "saveText", "获取文本", "读取文本", "保存文本"),
            "wait" to setOf("sleep", "pause", "等待", "暂停"),
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
        ),
    )

    override fun normalize(rawKeyword: String): String? {
        return delegate.normalize(rawKeyword)
    }
}
