package com.soluna.ui.autotest.dsl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.soluna.ui.autotest.core.model.CaseDefinition
import com.soluna.ui.autotest.core.model.ElementCatalogDefinition
import com.soluna.ui.autotest.core.model.FragmentCatalogDefinition
import com.soluna.ui.autotest.schema.JsonSchemaDslValidator

class YamlCaseParser(
    private val yamlMapper: ObjectMapper = YamlPlanParser.defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
    private val policyValidator: DslPolicyValidator = DslPolicyValidator(),
    private val actionNormalizer: DslActionNormalizer = DslActionNormalizer(yamlMapper),
) : DslParser<CaseDefinition> {
    override fun parse(content: String): CaseDefinition {
        val caseNode = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = CASE_SCHEMA_RESOURCE,
            value = caseNode,
        ) + policyValidator.validateCase(caseNode)

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.treeToValue(
            actionNormalizer.normalizeCase(caseNode),
            CaseDefinition::class.java,
        )
    }

    companion object {
        const val CASE_SCHEMA_RESOURCE = "/schemas/v1/case.schema.json"
    }
}

class YamlElementCatalogParser(
    private val yamlMapper: ObjectMapper = YamlPlanParser.defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
    private val policyValidator: DslPolicyValidator = DslPolicyValidator(),
) : DslParser<ElementCatalogDefinition> {
    override fun parse(content: String): ElementCatalogDefinition {
        val elementCatalogNode = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = ELEMENT_CATALOG_SCHEMA_RESOURCE,
            value = elementCatalogNode,
        ) + policyValidator.validateElementCatalog(elementCatalogNode)

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.readValue(content)
    }

    companion object {
        const val ELEMENT_CATALOG_SCHEMA_RESOURCE = "/schemas/v1/element-catalog.schema.json"
    }
}

class YamlFragmentCatalogParser(
    private val yamlMapper: ObjectMapper = YamlPlanParser.defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
    private val policyValidator: DslPolicyValidator = DslPolicyValidator(),
    private val actionNormalizer: DslActionNormalizer = DslActionNormalizer(yamlMapper),
) : DslParser<FragmentCatalogDefinition> {
    override fun parse(content: String): FragmentCatalogDefinition {
        val fragmentCatalogNode = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = FRAGMENT_CATALOG_SCHEMA_RESOURCE,
            value = fragmentCatalogNode,
        ) + policyValidator.validateFragmentCatalog(fragmentCatalogNode)

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.treeToValue(
            actionNormalizer.normalizeFragmentCatalog(fragmentCatalogNode),
            FragmentCatalogDefinition::class.java,
        )
    }

    companion object {
        const val FRAGMENT_CATALOG_SCHEMA_RESOURCE = "/schemas/v1/fragment-catalog.schema.json"
    }
}
