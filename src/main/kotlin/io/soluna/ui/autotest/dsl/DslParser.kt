package io.soluna.ui.autotest.dsl

interface DslParser<T> {
    fun parse(content: String): T
}
