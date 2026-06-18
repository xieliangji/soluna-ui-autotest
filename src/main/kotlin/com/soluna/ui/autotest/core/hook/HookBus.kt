package com.soluna.ui.autotest.core.hook

interface HookConsumer {
    val id: String

    fun supports(event: HookEvent): Boolean

    fun handle(event: HookEvent)
}

interface HookBus {
    fun publish(event: HookEvent)
}

class SimpleHookBus(
    consumers: List<HookConsumer> = emptyList(),
) : HookBus {
    private val consumers = consumers.toMutableList()

    fun register(consumer: HookConsumer) {
        consumers += consumer
    }

    override fun publish(event: HookEvent) {
        consumers
            .filter { it.supports(event) }
            .forEach { it.handle(event) }
    }
}
