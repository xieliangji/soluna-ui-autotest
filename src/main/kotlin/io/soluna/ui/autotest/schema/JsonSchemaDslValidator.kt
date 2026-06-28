package io.soluna.ui.autotest.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.soluna.ui.autotest.dsl.DslViolation

class JsonSchemaDslValidator(
    private val jsonMapper: ObjectMapper = defaultJsonMapper(),
) {
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    fun validate(
        schemaResource: String,
        value: JsonNode,
    ): List<DslViolation> {
        val schemaNode = readSchema(schemaResource)
        val schema = schemaFactory.getSchema(schemaNode)
        return schema.validate(value).map { message ->
            DslViolation(
                path = message.instanceLocation.toString(),
                message = message.message,
            )
        }.sortedWith(compareBy({ it.path }, { it.message }))
    }

    private fun readSchema(schemaResource: String): JsonNode {
        val stream = JsonSchemaDslValidator::class.java.getResourceAsStream(schemaResource)
            ?: throw IllegalArgumentException("Schema resource not found: $schemaResource")
        return stream.use { jsonMapper.readTree(it) }
    }

    companion object {
        fun defaultJsonMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
        }
    }
}
