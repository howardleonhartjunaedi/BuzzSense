package com.buzzsense.veritas.shared.guardrails

/** Thrown when a graph node calls a tool outside its allowlist. */
class ToolNotPermittedException(node: String, tool: String) :
    IllegalStateException("Node '$node' is not allowed to call tool '$tool'")

/**
 * Guardrail "Tool permissions": a per-node allowlist. Every tool is read-only and
 * the agent has no share or message tool at all, so nothing can leave the phone
 * without the parent's tap on the Review screen.
 */
class ToolPermissions(private val allow: Map<String, Set<String>>) {

    fun check(node: String, tool: String) {
        if (tool !in allow[node].orEmpty()) throw ToolNotPermittedException(node, tool)
    }

    fun allowed(node: String): Set<String> = allow[node].orEmpty()

    companion object {
        const val NODE_DETECT = "detect"
        const val NODE_CONTEXT = "context"
        const val NODE_RETRIEVE = "retrieve"
        const val NODE_COMPOSE = "compose"
        const val NODE_VALIDATE = "validate"

        const val TOOL_DETECTOR = "detect_wingbeat"
        const val TOOL_WEATHER = "san_juan_weather"
        const val TOOL_RISK = "outbreak_risk_level"
        const val TOOL_GUIDANCE = "search_guidance"

        /** The BuzzAlert graph policy. The compose (LLM) node gets no tools. */
        val BUZZALERT = ToolPermissions(
            mapOf(
                NODE_DETECT to setOf(TOOL_DETECTOR),
                NODE_CONTEXT to setOf(TOOL_WEATHER, TOOL_RISK),
                NODE_RETRIEVE to setOf(TOOL_GUIDANCE),
                NODE_COMPOSE to emptySet(),
                NODE_VALIDATE to emptySet(),
            )
        )
    }
}
