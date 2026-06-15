package com.ugreen.iot.soluna.autotest.dsl

interface DslParser<T> {
    fun parse(content: String): T
}
