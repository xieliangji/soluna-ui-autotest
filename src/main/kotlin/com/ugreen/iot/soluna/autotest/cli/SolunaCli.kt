package com.ugreen.iot.soluna.autotest.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.report.LocalReportWriter
import com.ugreen.iot.soluna.autotest.runner.PlanRunRequest
import com.ugreen.iot.soluna.autotest.runner.PlanRunResult
import com.ugreen.iot.soluna.autotest.runner.PlanRunner
import java.nio.file.Path
import kotlin.system.exitProcess

object SolunaCli {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(SolunaCliApplication().run(args))
    }
}

class SolunaCliApplication(
    private val runPlan: (PlanRunRequest) -> PlanRunResult = { request -> PlanRunner().run(request) },
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
) {
    fun run(
        args: Array<String>,
        stdout: Appendable = System.out,
        stderr: Appendable = System.err,
    ): Int {
        return try {
            when (val command = parse(args)) {
                CliCommand.Help -> {
                    stdout.appendLine(usage())
                    0
                }
                is CliCommand.Run -> runPlanCommand(command, stdout)
            }
        } catch (err: CliUsageException) {
            stderr.appendLine(err.message ?: "Invalid CLI arguments")
            stderr.appendLine()
            stderr.appendLine(usage())
            2
        } catch (err: Throwable) {
            stderr.appendLine("Soluna run failed: ${err.message ?: err::class.simpleName}")
            1
        }
    }

    private fun runPlanCommand(
        command: CliCommand.Run,
        stdout: Appendable,
    ): Int {
        val reportRoot = command.reportRoot ?: Path.of("build/soluna-runs")
        val result = runPlan(
            PlanRunRequest(
                planPath = command.planPath,
                runId = command.runId ?: defaultRunId(),
                parameterOverrides = command.parameters,
                reportWriter = LocalReportWriter(outputRoot = reportRoot),
                localArtifactRoot = reportRoot,
            ),
        )

        stdout.appendLine("Soluna run completed")
        stdout.appendLine("runId: ${result.executionResult.runId}")
        stdout.appendLine("plan: ${result.plan.id}")
        stdout.appendLine("status: ${result.executionResult.status.name.lowercase()}")
        result.report?.htmlFile?.let { report ->
            stdout.appendLine("report: ${report.toAbsolutePath().normalize()}")
        }
        if (result.traceArtifacts.isNotEmpty()) {
            stdout.appendLine("traceArtifacts: ${result.traceArtifacts.size}")
        }
        result.artifactUploads?.let { uploads ->
            stdout.appendLine(
                "uploads: completed=${uploads.completed}, uploaded=${uploads.uploadedCount}, failed=${uploads.failedCount}, abandoned=${uploads.abandonedCount}",
            )
        }
        result.localArtifactCleanup?.let { cleanup ->
            stdout.appendLine("localCleanup: deleted=${cleanup.deleted}${cleanup.reason?.let { ", reason=$it" }.orEmpty()}")
        }

        return if (result.executionResult.status == command.expectedStatus) {
            0
        } else {
            1
        }
    }

    private fun parse(args: Array<String>): CliCommand {
        if (args.isEmpty() || args.singleOrNull() in setOf("-h", "--help", "help")) {
            return CliCommand.Help
        }
        if (args.first() != "run") {
            throw CliUsageException("Expected command 'run'")
        }

        var planPath: Path? = null
        var runId: String? = null
        var reportRoot: Path? = null
        var expectedStatus = ExecutionStatus.PASSED
        val parameters = linkedMapOf<String, JsonNode>()

        var index = 1
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "-h" || arg == "--help" -> return CliCommand.Help
                arg == "--run-id" -> {
                    runId = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--run-id=") -> runId = arg.substringAfter("=")
                arg == "--report-root" -> {
                    reportRoot = Path.of(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--report-root=") -> reportRoot = Path.of(arg.substringAfter("="))
                arg == "--param" -> {
                    putParameter(parameters, args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--param=") -> putParameter(parameters, arg.substringAfter("="))
                arg == "--expect" || arg == "--expected-status" -> {
                    expectedStatus = parseExpectedStatus(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--expect=") || arg.startsWith("--expected-status=") -> {
                    expectedStatus = parseExpectedStatus(arg.substringAfter("="))
                }
                arg.startsWith("-") -> throw CliUsageException("Unknown option '$arg'")
                planPath == null -> planPath = Path.of(arg)
                else -> throw CliUsageException("Only one plan path can be provided")
            }
            index += 1
        }

        return CliCommand.Run(
            planPath = planPath ?: throw CliUsageException("Plan path is required"),
            runId = runId?.takeIf { it.isNotBlank() },
            reportRoot = reportRoot,
            parameters = parameters,
            expectedStatus = expectedStatus,
        )
    }

    private fun putParameter(
        parameters: MutableMap<String, JsonNode>,
        raw: String,
    ) {
        val key = raw.substringBefore("=", missingDelimiterValue = "").trim()
        val value = raw.substringAfter("=", missingDelimiterValue = "")
        if (key.isBlank() || !raw.contains("=")) {
            throw CliUsageException("--param requires key=value")
        }
        parameters[key] = parseParameterValue(value)
    }

    private fun parseParameterValue(raw: String): JsonNode {
        return runCatching { objectMapper.readTree(raw) }
            .getOrElse { objectMapper.valueToTree(raw) }
    }

    private fun parseExpectedStatus(raw: String): ExecutionStatus {
        return when (raw.lowercase()) {
            "passed", "pass" -> ExecutionStatus.PASSED
            "failed", "fail" -> ExecutionStatus.FAILED
            else -> throw CliUsageException("Expected status must be passed or failed")
        }
    }

    private fun defaultRunId(): String {
        return "run-${System.currentTimeMillis()}"
    }

    private fun Array<String>.valueAfter(
        index: Int,
        option: String,
    ): String {
        return getOrNull(index + 1)
            ?.takeUnless { it.startsWith("-") }
            ?: throw CliUsageException("$option requires a value")
    }

    private fun usage(): String {
        return """
            Usage:
              soluna run <plan.yaml> [--run-id <id>] [--param key=value] [--report-root <dir>] [--expect passed|failed]

            Required:
              <plan.yaml>                Plan path. Device, artifacts, cases, elements, data, and fragments are resolved from the plan reference chain.

            Optional:
              --run-id <id>              Override generated run id.
              --param key=value          Runtime parameter override. Can be repeated.
              --report-root <dir>        Local report/artifact root. Default: build/soluna-runs.
              --expect passed|failed     Expected execution status. Default: passed.
        """.trimIndent()
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
        }
    }
}

private sealed class CliCommand {
    data object Help : CliCommand()

    data class Run(
        val planPath: Path,
        val runId: String?,
        val reportRoot: Path?,
        val parameters: Map<String, JsonNode>,
        val expectedStatus: ExecutionStatus,
    ) : CliCommand()
}

private class CliUsageException(message: String) : IllegalArgumentException(message)
