package com.ugreen.iot.soluna.autotest.appium.ext

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.net.URI

class SolunaAppiumExtHttpClient(
    private val transport: SolunaAppiumExtTransport,
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
) : SolunaAppiumExtClient {
    constructor(baseUrl: URI) : this(
        transport = JavaNetSolunaAppiumExtTransport(baseUrl),
    )

    override fun getDevice(udid: String): DeviceLookupResult {
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "GET",
                path = "/soluna/device",
                query = mapOf("udid" to udid),
            ),
        )
        return readValue(response, setOf(200, 404), object : TypeReference<DeviceLookupResult>() {})
    }

    override fun listDevices(): ListDevicesResult {
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "GET",
                path = "/soluna/devices",
            ),
        )
        return readValue(response, setOf(200), object : TypeReference<ListDevicesResult>() {})
    }

    override fun getWdaBundle(udid: String): WdaBundleLookupResult {
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "GET",
                path = "/soluna/ios/wda-bundle",
                query = mapOf("udid" to udid),
            ),
        )
        return readValue(response, setOf(200, 404), object : TypeReference<WdaBundleLookupResult>() {})
    }

    override fun executeCommand(request: CommandExecuteRequest): CommandExecuteResult {
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "POST",
                path = "/soluna/command",
                body = writeJson(request),
            ),
        )
        return readValue(response, setOf(200, 422), object : TypeReference<CommandExecuteResult>() {})
    }

    override fun createLogSession(request: CreateLogSessionRequest): CreateLogSessionResult {
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "POST",
                path = "/soluna/logs/sessions",
                body = writeJson(request),
            ),
        )
        return readValue(response, setOf(201), object : TypeReference<CreateLogSessionResult>() {})
    }

    override fun readLogSession(request: ReadLogSessionRequest): ReadLogSessionResult {
        val query = buildMap {
            request.cursor?.let { put("cursor", it.toString()) }
            request.limit?.let { put("limit", it.toString()) }
        }
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "GET",
                path = "/soluna/logs/sessions/${request.sessionId}",
                query = query,
            ),
        )
        return readValue(response, setOf(200), object : TypeReference<ReadLogSessionResult>() {})
    }

    override fun deleteLogSession(request: DeleteLogSessionRequest): DeleteLogSessionResult {
        val response = transport.exchange(
            SolunaAppiumExtHttpRequest(
                method = "DELETE",
                path = "/soluna/logs/sessions/${request.sessionId}",
            ),
        )
        return readValue(response, setOf(200), object : TypeReference<DeleteLogSessionResult>() {})
    }

    private fun writeJson(value: Any): String {
        return objectMapper.writeValueAsString(value)
    }

    private fun <T> readValue(
        response: SolunaAppiumExtHttpResponse,
        allowedStatuses: Set<Int>,
        valueType: TypeReference<T>,
    ): T {
        val root = readResponseRoot(response)
        if (response.statusCode !in allowedStatuses) {
            throw toException(response, root)
        }

        val value = root.get("value")
            ?: throw SolunaAppiumExtHttpException(
                statusCode = response.statusCode,
                error = "missing_value",
                detail = "Response body does not contain 'value'",
                responseBody = response.body,
            )
        return objectMapper.convertValue(value, valueType)
    }

    private fun readResponseRoot(response: SolunaAppiumExtHttpResponse): JsonNode {
        return try {
            objectMapper.readTree(response.body)
        } catch (err: Exception) {
            throw SolunaAppiumExtHttpException(
                statusCode = response.statusCode,
                error = "invalid_json",
                detail = err.message ?: "Response body is not valid JSON",
                responseBody = response.body,
            )
        }
    }

    private fun toException(
        response: SolunaAppiumExtHttpResponse,
        root: JsonNode,
    ): SolunaAppiumExtHttpException {
        val value = root.get("value")
        val error = value?.get("error")?.takeIf { it.isTextual }?.asText()
        val message = value?.get("message")?.takeIf { it.isTextual }?.asText()
        return SolunaAppiumExtHttpException(
            statusCode = response.statusCode,
            error = error,
            detail = message ?: "Unexpected soluna-appium-ext response status ${response.statusCode}",
            responseBody = response.body,
        )
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
}

class SolunaAppiumExtHttpException(
    val statusCode: Int,
    val error: String?,
    val detail: String,
    @Suppress("unused") val responseBody: String,
) : RuntimeException("soluna-appium-ext HTTP $statusCode${error?.let { " [$it]" }.orEmpty()}: $detail")
