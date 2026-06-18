package com.soluna.ui.autotest.appium.ext

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class JavaNetSolunaAppiumExtTransport(
    private val baseUrl: URI,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : SolunaAppiumExtTransport {
    override fun exchange(request: SolunaAppiumExtHttpRequest): SolunaAppiumExtHttpResponse {
        val httpRequest = HttpRequest.newBuilder(resolveUri(request))
            .header("Accept", "application/json")
            .method(
                request.method,
                request.body?.let {
                    HttpRequest.BodyPublishers.ofString(it)
                } ?: HttpRequest.BodyPublishers.noBody(),
            )
            .apply {
                if (request.body != null) {
                    header("Content-Type", "application/json")
                }
            }
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        return SolunaAppiumExtHttpResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    private fun resolveUri(request: SolunaAppiumExtHttpRequest): URI {
        val base = baseUrl.toString().trimEnd('/')
        val path = request.path.ensureLeadingSlash()
        val query = request.query
            .filterValues { it.isNotBlank() }
            .entries
            .joinToString(separator = "&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        return URI.create(
            if (query.isBlank()) {
                "$base$path"
            } else {
                "$base$path?$query"
            },
        )
    }

    private fun String.ensureLeadingSlash(): String {
        return if (startsWith("/")) this else "/$this"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
