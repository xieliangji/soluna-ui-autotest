package com.ugreen.iot.soluna.autotest.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ugreen.iot.soluna.autotest.dsl.DslParser
import com.ugreen.iot.soluna.autotest.dsl.DslValidationException
import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
import com.ugreen.iot.soluna.autotest.schema.JsonSchemaDslValidator

class YamlNotificationSenderConfigParser(
    private val yamlMapper: ObjectMapper = YamlPlanParser.defaultYamlMapper(),
    private val schemaValidator: JsonSchemaDslValidator = JsonSchemaDslValidator(),
) : DslParser<NotificationSenderConfigDefinition> {
    override fun parse(content: String): NotificationSenderConfigDefinition {
        val node = yamlMapper.readTree(content)
        val violations = schemaValidator.validate(
            schemaResource = NOTIFICATION_SENDER_SCHEMA_RESOURCE,
            value = node,
        )

        if (violations.isNotEmpty()) {
            throw DslValidationException(violations)
        }

        return yamlMapper.readValue(content)
    }

    companion object {
        const val NOTIFICATION_SENDER_SCHEMA_RESOURCE = "/schemas/v1/notification-sender.schema.json"
    }
}
