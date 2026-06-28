package io.soluna.ui.autotest.report

import java.nio.file.Files
import java.nio.file.Path

object ReportLinkRewriter {
    fun rewrite(
        htmlFile: Path,
        links: Map<String, String>,
    ) {
        var html = Files.readString(htmlFile)
        links.forEach { (localHref, publishedHref) ->
            html = html.replace(
                "href=\"${localHref.escapeAttribute()}\"",
                "href=\"${publishedHref.escapeAttribute()}\"",
            )
        }
        Files.writeString(htmlFile, html)
    }

    private fun String.escapeAttribute(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
