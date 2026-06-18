package com.soluna.ui.autotest.artifact

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.soluna.ui.autotest.dsl.DslParser
import com.soluna.ui.autotest.dsl.DslValidationException
import com.soluna.ui.autotest.dsl.YamlPlanParser
import com.soluna.ui.autotest.schema.JsonSchemaDslValidator

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
