package com.soluna.ui.autotest.appium.ext

data class SolunaAppiumExtHttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class SolunaAppiumExtHttpResponse(
    val statusCode: Int,
    val body: String,
)

interface SolunaAppiumExtTransport {
    fun exchange(request: SolunaAppiumExtHttpRequest): SolunaAppiumExtHttpResponse
}
