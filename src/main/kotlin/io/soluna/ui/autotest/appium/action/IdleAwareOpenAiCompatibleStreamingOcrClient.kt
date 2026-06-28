package io.soluna.ui.autotest.appium.action

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.http.StreamResponse
import com.openai.models.Reasoning
import com.openai.models.ReasoningEffort
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseInputText
import com.openai.models.responses.ResponseStreamEvent
import com.soluna.ktvisual.ocr.multimodal.MultimodalOcrClient
import com.soluna.ktvisual.ocr.multimodal.MultimodalOcrException
import com.soluna.ktvisual.ocr.multimodal.MultimodalOcrRequest
import com.soluna.ktvisual.ocr.multimodal.OpenAiCompatibleMultimodalOcrConfig
import com.soluna.ktvisual.ocr.multimodal.OpenAiCompatibleStreamEvent
import java.net.URI
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class IdleAwareOpenAiCompatibleStreamingOcrClient private constructor(
    private val baseUrl: URI,
    private val model: String,
    private val apiKey: String?,
    private val extraHeaders: Map<String, String>,
    private val reasoningEffort: String?,
    private val streamHttpTimeout: Duration,
    private val streamIdleTimeout: Duration,
    private val onStreamEvent: (OpenAiCompatibleStreamEvent) -> Unit,
    private val systemPrompt: String,
    private val client: OpenAIClient = sdkClient(baseUrl, apiKey, streamHttpTimeout, extraHeaders),
) : MultimodalOcrClient {
    init {
        require(model.isNotBlank()) { "model must not be blank." }
        require(!streamHttpTimeout.isNegative && !streamHttpTimeout.isZero) {
            "streamHttpTimeout must be positive."
        }
        require(!streamIdleTimeout.isNegative && !streamIdleTimeout.isZero) {
            "streamIdleTimeout must be positive."
        }
        require(reasoningEffort == null || reasoningEffort.isNotBlank()) {
            "reasoningEffort must not be blank."
        }
        require(systemPrompt.isNotBlank()) { "systemPrompt must not be blank." }
    }

    override fun complete(request: MultimodalOcrRequest): String =
        try {
            completeStreaming(params(request))
        } catch (err: Exception) {
            if (err is MultimodalOcrException) {
                throw err
            }
            throw MultimodalOcrException("Multimodal OCR request failed.", err)
        }

    private fun params(request: MultimodalOcrRequest): ResponseCreateParams {
        val message = ResponseInputItem.Message.builder()
            .role(ResponseInputItem.Message.Role.USER)
            .addContent(
                ResponseInputText.builder()
                    .text(request.prompt)
                    .build(),
            )
            .addContent(
                ResponseInputImage.builder()
                    .imageUrl(request.dataUrl())
                    .detail(ResponseInputImage.Detail.HIGH)
                    .build(),
            )
            .build()
        val builder = ResponseCreateParams.builder()
            .model(model)
            .instructions(systemPrompt)
            .inputOfResponse(listOf(ResponseInputItem.ofMessage(message)))
            .store(false)

        if (!reasoningEffort.isNullOrBlank()) {
            builder.reasoning(
                Reasoning.builder()
                    .effort(ReasoningEffort.of(reasoningEffort))
                    .build(),
            )
        }
        return builder.build()
    }

    private fun completeStreaming(params: ResponseCreateParams): String {
        val content = StringBuilder()
        val lastOutputAt = AtomicLong(System.nanoTime())
        val idleTimedOut = AtomicBoolean(false)
        val streamResponseRef = AtomicReference<StreamResponse<ResponseStreamEvent>?>()
        val checkIntervalMs = streamIdleTimeout.toMillis().coerceAtMost(1_000).coerceAtLeast(100)
        val watchdog = scheduler.scheduleWithFixedDelay(
            {
                val idleForNanos = System.nanoTime() - lastOutputAt.get()
                if (idleForNanos >= streamIdleTimeout.toNanos() && idleTimedOut.compareAndSet(false, true)) {
                    streamResponseRef.get()?.close()
                }
            },
            checkIntervalMs,
            checkIntervalMs,
            TimeUnit.MILLISECONDS,
        )

        try {
            val streamResponse = client.responses().createStreaming(params)
            streamResponseRef.set(streamResponse)
            streamResponse.stream().forEach { event ->
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Multimodal OCR stream was cancelled.")
                }
                if (appendStreamingEvent(event, content)) {
                    lastOutputAt.set(System.nanoTime())
                }
            }
        } catch (err: Exception) {
            if (idleTimedOut.get()) {
                throw MultimodalOcrException(
                    "Multimodal OCR stream was idle for ${streamIdleTimeout.toMillis()}ms without reasoning or content output.",
                    err,
                )
            }
            throw err
        } finally {
            watchdog.cancel(true)
            streamResponseRef.get()?.close()
        }

        if (idleTimedOut.get()) {
            throw MultimodalOcrException(
                "Multimodal OCR stream was idle for ${streamIdleTimeout.toMillis()}ms without reasoning or content output.",
                null,
            )
        }
        if (content.isBlank()) {
            throw MultimodalOcrException("Multimodal OCR stream did not contain message content.", null)
        }
        return content.toString()
    }

    private fun appendStreamingEvent(
        event: ResponseStreamEvent,
        content: StringBuilder,
    ): Boolean {
        var emitted = false
        event.reasoningSummaryTextDelta().ifPresent { delta ->
            val text = delta.delta()
            if (text.isNotEmpty()) {
                onStreamEvent(OpenAiCompatibleStreamEvent.Reasoning(text))
                emitted = true
            }
        }
        event.reasoningTextDelta().ifPresent { delta ->
            val text = delta.delta()
            if (text.isNotEmpty()) {
                onStreamEvent(OpenAiCompatibleStreamEvent.Reasoning(text))
                emitted = true
            }
        }
        event.outputTextDelta().ifPresent { delta ->
            val text = delta.delta()
            content.append(text)
            if (text.isNotEmpty()) {
                onStreamEvent(OpenAiCompatibleStreamEvent.Content(text))
                emitted = true
            }
        }
        return emitted
    }

    companion object {
        private val schedulerThreadIndex = AtomicInteger()
        private val scheduler = Executors.newScheduledThreadPool(1) { runnable ->
            Thread(runnable, "soluna-multimodal-ocr-idle-watchdog-${schedulerThreadIndex.incrementAndGet()}").apply {
                isDaemon = true
            }
        }

        fun fromConfig(
            config: OpenAiCompatibleMultimodalOcrConfig,
            streamHttpTimeout: Duration,
            streamIdleTimeout: Duration,
            onStreamEvent: (OpenAiCompatibleStreamEvent) -> Unit,
        ): IdleAwareOpenAiCompatibleStreamingOcrClient =
            IdleAwareOpenAiCompatibleStreamingOcrClient(
                baseUrl = config.baseUrl,
                model = config.model,
                apiKey = config.apiKey,
                extraHeaders = config.extraHeaders,
                reasoningEffort = config.reasoningEffort,
                streamHttpTimeout = streamHttpTimeout,
                streamIdleTimeout = streamIdleTimeout,
                onStreamEvent = onStreamEvent,
                systemPrompt = config.systemPrompt,
            )

        private fun sdkClient(
            baseUrl: URI,
            apiKey: String?,
            timeout: Duration,
            extraHeaders: Map<String, String>,
        ): OpenAIClient {
            val builder = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl.toString().trimEnd('/'))
                .timeout(timeout)
            if (!apiKey.isNullOrBlank()) {
                builder.apiKey(apiKey)
            }
            extraHeaders.forEach { (name, value) ->
                builder.putHeader(name, value)
            }
            return builder.build()
        }
    }
}
