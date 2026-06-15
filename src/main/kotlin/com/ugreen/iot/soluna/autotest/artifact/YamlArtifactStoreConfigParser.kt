package com.ugreen.iot.soluna.autotest.artifact

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ugreen.iot.soluna.autotest.dsl.DslParser
import com.ugreen.iot.soluna.autotest.dsl.DslValidationException
import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
import com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidator

class YamlArtifactStoreConfigParser(
    private val yamlMapper: ObjectMapper = YamlPlanParser.defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
) : DslParser<ArtifactStoreConfigDefinition> {
    override fun parse(content: String): ArtifactStoreConfigDefinition {
        val node = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = ARTIFACT_STORE_SCHEMA_RESOURCE,
            value = node,
        )

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.readValue(content)
    }

    companion object {
        const val ARTIFACT_STORE_SCHEMA_RESOURCE = "/schemas/v1/artifact-store.schema.json"
    }
}
