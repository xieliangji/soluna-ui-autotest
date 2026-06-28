package io.soluna.ui.autotest.dsl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.soluna.ui.autotest.core.model.PlanDefinition
import io.soluna.ui.autotest.schema.JsonSchemaDslValidator

class YamlPlanParser(
    private val yamlMapper: ObjectMapper = defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
    private val policyValidator: DslPolicyValidator = DslPolicyValidator(),
    private val actionNormalizer: DslActionNormalizer = DslActionNormalizer(yamlMapper),
) : DslParser<PlanDefinition> {
    override fun parse(content: String): PlanDefinition {
        val planNode = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = PLAN_SCHEMA_RESOURCE,
            value = planNode,
        ) + policyValidator.validatePlan(planNode)

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.treeToValue(
            actionNormalizer.normalizePlan(planNode),
            PlanDefinition::class.java,
        )
    }

    fun parseNode(content: String): JsonNode {
        return yamlMapper.readTree(content)
    }

    companion object {
        const val PLAN_SCHEMA_RESOURCE = "/schemas/v1/plan.schema.json"

        fun defaultYamlMapper(): ObjectMapper {
            return ObjectMapper(YAMLFactory())
                .registerModule(KotlinModule.Builder().build())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
}
