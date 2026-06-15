package com.ugreen.iot.soluna.autotest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ugreen.iot.soluna.autotest.dsl.DslParser
import com.ugreen.iot.soluna.autotest.dsl.DslValidationException
import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
import com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidator

class YamlParameterDataParser(
    private val yamlMapper: ObjectMapper = YamlPlanParser.defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
) : DslParser<ParameterDataDefinition> {
    override fun parse(content: String): ParameterDataDefinition {
        val node = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = PARAMETER_DATA_SCHEMA_RESOURCE,
            value = node,
        )

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.readValue(content)
    }

    companion object {
        const val PARAMETER_DATA_SCHEMA_RESOURCE = "/schemas/v1/parameter-data.schema.json"
    }
}
