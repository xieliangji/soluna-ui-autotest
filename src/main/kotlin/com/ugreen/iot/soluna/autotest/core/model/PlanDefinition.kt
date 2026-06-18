package com.ugreen.iot.soluna.autotest.core.model

import com.fasterxml.jackson.databind.JsonNode

data class PlanDefinition(
    val schemaVersion: String,
    val id: String,
    val name: String,
    val version: String? = null,
    val metadata: Map<String, JsonNode> = emptyMap(),
    val parameters: List<ParameterFileRef> = emptyList(),
    val fragmentRefs: List<FragmentFileRef> = emptyList(),
    val setupFragments: List<String> = emptyList(),
    val setupActions: List<ActionDefinition> = emptyList(),
    val caseSetupFragments: List<String> = emptyList(),
    val caseSetupActions: List<ActionDefinition> = emptyList(),
    val caseTeardownFragments: List<String> = emptyList(),
    val caseTeardownActions: List<ActionDefinition> = emptyList(),
    val teardownFragments: List<String> = emptyList(),
    val teardownActions: List<ActionDefinition> = emptyList(),
    val deviceConfig: String? = null,
    val artifactStore: String? = null,
    val app: AppDefinition? = null,
    val defaults: PlanDefaults = PlanDefaults(),
    val trace: TraceDefinition = TraceDefinition(),
    val localArtifacts: LocalArtifactsDefinition = LocalArtifactsDefinition(),
    val stages: List<StageDefinition> = emptyList(),
)

data class ParameterFileRef(
    val id: String,
    val file: String,
    val required: Boolean = true,
)

data class FragmentFileRef(
    val id: String,
    val file: String,
    val required: Boolean = true,
)

data class CaseFileRef(
    val id: String? = null,
    val file: String,
    val required: Boolean = true,
)

data class ElementFileRef(
    val id: String,
    val file: String,
    val required: Boolean = true,
)

data class AppDefinition(
    val id: String? = null,
    val name: String? = null,
    val platform: String? = null,
    val reset: Boolean? = null,
)

data class PlanDefaults(
    val implicitWaitMs: Long = 5_000,
    val actionWait: WaitDefinition? = null,
    val failureStrategy: String? = null,
    val retryStrategy: String? = null,
)

data class TraceDefinition(
    val screenshots: TraceScreenshotDefinition = TraceScreenshotDefinition(),
)

data class TraceScreenshotDefinition(
    val enabled: Boolean = false,
    val beforeAction: String = "onFailure",
    val retainBeforeActionCount: Int = 5,
    val upload: String = "onFailure",
)

data class LocalArtifactsDefinition(
    val cleanup: LocalArtifactCleanupDefinition = LocalArtifactCleanupDefinition(),
)

data class LocalArtifactCleanupDefinition(
    val mode: String = "never",
)

data class StageDefinition(
    val id: String,
    val name: String,
    val initialState: String? = null,
    val parameters: Map<String, JsonNode> = emptyMap(),
    val setupFragments: List<String> = emptyList(),
    val setupActions: List<ActionDefinition> = emptyList(),
    val caseSetupFragments: List<String> = emptyList(),
    val caseSetupActions: List<ActionDefinition> = emptyList(),
    val caseTeardownFragments: List<String> = emptyList(),
    val caseTeardownActions: List<ActionDefinition> = emptyList(),
    val teardownFragments: List<String> = emptyList(),
    val teardownActions: List<ActionDefinition> = emptyList(),
    val caseRefs: List<CaseFileRef> = emptyList(),
    val cases: List<CaseDefinition> = emptyList(),
)

data class CaseDefinition(
    val id: String,
    val name: String,
    val dataRefs: List<ParameterFileRef> = emptyList(),
    val elementRefs: List<ElementFileRef> = emptyList(),
    val setupFragments: List<String> = emptyList(),
    val setupActions: List<ActionDefinition> = emptyList(),
    val caseSetupFragments: List<String> = emptyList(),
    val caseSetupActions: List<ActionDefinition> = emptyList(),
    val caseTeardownFragments: List<String> = emptyList(),
    val caseTeardownActions: List<ActionDefinition> = emptyList(),
    val teardownFragments: List<String> = emptyList(),
    val teardownActions: List<ActionDefinition> = emptyList(),
    val parameters: Map<String, JsonNode> = emptyMap(),
    val actions: List<ActionDefinition> = emptyList(),
)

data class ActionDefinition(
    val id: String? = null,
    val keyword: String,
    val name: String? = null,
    val target: String? = null,
    val element: String? = null,
    val locator: LocatorDefinition? = null,
    val value: JsonNode? = null,
    val wait: WaitDefinition? = null,
    val assertion: AssertionDefinition? = null,
    val resourceId: String? = null,
    val args: Map<String, JsonNode> = emptyMap(),
    val conditionAction: ActionDefinition? = null,
    val thenActions: List<ActionDefinition> = emptyList(),
    val elseActions: List<ActionDefinition> = emptyList(),
)

data class LocatorDefinition(
    val strategy: String,
    val value: String,
    val args: Map<String, JsonNode> = emptyMap(),
)

data class WaitDefinition(
    val timeoutMs: Long? = null,
    val intervalMs: Long? = null,
    val condition: Map<String, JsonNode> = emptyMap(),
)

data class AssertionDefinition(
    val type: String,
    val target: String? = null,
    val expected: JsonNode? = null,
    val args: Map<String, JsonNode> = emptyMap(),
)

data class ElementCatalogDefinition(
    val schemaVersion: String,
    val id: String,
    val description: String? = null,
    val elements: Map<String, ElementDefinition> = emptyMap(),
)

data class ElementDefinition(
    val strategy: String? = null,
    val value: String? = null,
    val args: Map<String, JsonNode> = emptyMap(),
    val android: LocatorDefinition? = null,
    val ios: LocatorDefinition? = null,
) {
    fun locatorFor(platform: String?): LocatorDefinition {
        val normalizedPlatform = platform?.lowercase()
        return when (normalizedPlatform) {
            "android" -> android ?: commonLocator()
            "ios" -> ios ?: commonLocator()
            null -> commonLocator()
            else -> error("Unsupported element platform '$platform'")
        } ?: error("Element does not define locator for platform '${platform ?: "default"}'")
    }

    private fun commonLocator(): LocatorDefinition? {
        return strategy?.let { locatorStrategy ->
            LocatorDefinition(
                strategy = locatorStrategy,
                value = value ?: error("Element with strategy '$locatorStrategy' requires value"),
                args = args,
            )
        }
    }
}

data class FragmentCatalogDefinition(
    val schemaVersion: String,
    val id: String,
    val description: String? = null,
    val fragments: Map<String, FragmentDefinition> = emptyMap(),
)

data class FragmentDefinition(
    val name: String? = null,
    val elementRefs: List<ElementFileRef> = emptyList(),
    val actions: List<ActionDefinition> = emptyList(),
)
