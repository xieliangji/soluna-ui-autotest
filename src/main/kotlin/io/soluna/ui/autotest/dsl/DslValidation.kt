package io.soluna.ui.autotest.dsl

data class DslViolation(
    val path: String,
    val message: String,
)

class DslValidationException(
    val violations: List<DslViolation>,
) : IllegalArgumentException(
    violations.joinToString(separator = "\n") { "${it.path}: ${it.message}" },
)
