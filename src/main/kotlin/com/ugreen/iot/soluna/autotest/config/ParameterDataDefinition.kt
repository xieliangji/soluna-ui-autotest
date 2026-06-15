package com.ugreen.iot.soluna.autotest.config

import com.fasterxml.jackson.databind.JsonNode

data class ParameterDataDefinition(
    val schemaVersion: String,
    val id: String,
    val description: String? = null,
    val values: JsonNode,
    val secrets: List<String> = emptyList(),
)
