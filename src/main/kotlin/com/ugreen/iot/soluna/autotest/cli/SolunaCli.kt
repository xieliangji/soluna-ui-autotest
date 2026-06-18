package com.ugreen.iot.soluna.autotest.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
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
    private val runDebug: (AppiumDebugRequest) -> AppiumDebugResult = { request -> AppiumDebugManager().run(request) },
    private val runDebugShell: (AppiumDebugShellRequest, Appendable) -> AppiumDebugShellResult = { request, stdout ->
        AppiumDebugManager().runShell(request, stdout)
    },
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
                is CliCommand.Debug -> runDebugCommand(command, stdout)
                is CliCommand.DebugShell -> runDebugShellCommand(command, stdout)
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

    private fun runDebugCommand(
        command: CliCommand.Debug,
        stdout: Appendable,
    ): Int {
        val result = runDebug(
            AppiumDebugRequest(
                planPath = command.planPath,
                action = command.action,
                keepInfrastructure = command.keepInfrastructure,
            ),
        )
        stdout.appendLine("Soluna debug completed")
        stdout.appendLine("action: ${result.action}")
        stdout.appendLine("server: ${result.serverUrl}")
        result.wdaUrl?.let { stdout.appendLine("wda: $it") }
        stdout.appendLine("session: ${result.sessionId}")
        stdout.appendLine(result.output)
        return 0
    }

    private fun runDebugShellCommand(
        command: CliCommand.DebugShell,
        stdout: Appendable,
    ): Int {
        val result = runDebugShell(
            AppiumDebugShellRequest(
                planPath = command.planPath,
                keepInfrastructure = command.keepInfrastructure,
            ),
            stdout,
        )
        stdout.appendLine("Soluna debug shell completed")
        stdout.appendLine("server: ${result.serverUrl}")
        result.wdaUrl?.let { stdout.appendLine("wda: $it") }
        stdout.appendLine("session: ${result.sessionId}")
        stdout.appendLine("commands: ${result.commandCount}")
        return 0
    }

    private fun parse(args: Array<String>): CliCommand {
        if (args.isEmpty() || args.singleOrNull() in setOf("-h", "--help", "help")) {
            return CliCommand.Help
        }
        if (args.first() == "debug") {
            return parseDebug(args)
        }
        if (args.first() != "run") {
            throw CliUsageException("Expected command 'run' or 'debug'")
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

    private fun parseDebug(args: Array<String>): CliCommand {
        if (args.size < 3) {
            throw CliUsageException("debug requires <plan.yaml> and an action")
        }
        var planPath: Path? = null
        var actionName: String? = null
        var keepInfrastructure = false
        var output: Path? = null
        var xRatio: Double? = null
        var yRatio: Double? = null
        var strategy: String? = null
        var locatorValue: String? = null
        var text: String? = null
        var clearFirst = true
        var elementXRatio = 0.5
        var elementYRatio = 0.5
        var template: Path? = null
        var threshold = 0.72
        var scales = listOf(0.8, 1.0, 1.2, 1.4)
        var roi: DebugRoi? = null
        var targetXRatio = 0.5
        var targetYRatio = 0.5

        var index = 1
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "-h" || arg == "--help" -> return CliCommand.Help
                arg == "--keep-infra" || arg == "--keep-infrastructure" -> keepInfrastructure = true
                arg == "--out" || arg == "--output" -> {
                    output = Path.of(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    output = Path.of(arg.substringAfter("="))
                }
                arg == "--x-ratio" -> {
                    xRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--x-ratio=") -> xRatio = parseRatio(arg.substringAfter("="), "--x-ratio")
                arg == "--y-ratio" -> {
                    yRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--y-ratio=") -> yRatio = parseRatio(arg.substringAfter("="), "--y-ratio")
                arg == "--strategy" || arg == "--by" -> {
                    strategy = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--strategy=") || arg.startsWith("--by=") -> strategy = arg.substringAfter("=")
                arg == "--locator" -> {
                    locatorValue = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--locator=") -> locatorValue = arg.substringAfter("=")
                arg == "--text" -> {
                    text = args.valueAfter(index, arg)
                    index += 1
                }
                arg.startsWith("--text=") -> text = arg.substringAfter("=")
                arg == "--clear-first" -> {
                    clearFirst = parseBoolean(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--clear-first=") -> clearFirst = parseBoolean(arg.substringAfter("="), "--clear-first")
                arg == "--element-x-ratio" -> {
                    elementXRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--element-x-ratio=") -> elementXRatio = parseRatio(arg.substringAfter("="), "--element-x-ratio")
                arg == "--element-y-ratio" -> {
                    elementYRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--element-y-ratio=") -> elementYRatio = parseRatio(arg.substringAfter("="), "--element-y-ratio")
                arg == "--template" -> {
                    template = Path.of(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--template=") -> template = Path.of(arg.substringAfter("="))
                arg == "--threshold" -> {
                    threshold = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--threshold=") -> threshold = parseRatio(arg.substringAfter("="), "--threshold")
                arg == "--scales" -> {
                    scales = parseScales(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--scales=") -> scales = parseScales(arg.substringAfter("="))
                arg == "--roi" -> {
                    roi = parseRoi(args.valueAfter(index, arg))
                    index += 1
                }
                arg.startsWith("--roi=") -> roi = parseRoi(arg.substringAfter("="))
                arg == "--target-x-ratio" -> {
                    targetXRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--target-x-ratio=") -> targetXRatio = parseRatio(arg.substringAfter("="), "--target-x-ratio")
                arg == "--target-y-ratio" -> {
                    targetYRatio = parseRatio(args.valueAfter(index, arg), arg)
                    index += 1
                }
                arg.startsWith("--target-y-ratio=") -> targetYRatio = parseRatio(arg.substringAfter("="), "--target-y-ratio")
                arg.startsWith("-") -> throw CliUsageException("Unknown debug option '$arg'")
                planPath == null -> planPath = Path.of(arg)
                actionName == null -> actionName = arg
                else -> throw CliUsageException("Unexpected debug argument '$arg'")
            }
            index += 1
        }

        if (actionName == "shell") {
            return CliCommand.DebugShell(
                planPath = planPath ?: throw CliUsageException("debug plan path is required"),
                keepInfrastructure = keepInfrastructure,
            )
        }

        val action = when (actionName) {
            "source" -> AppiumDebugAction.Source(output = output)
            "screenshot" -> AppiumDebugAction.Screenshot(
                output = output ?: throw CliUsageException("debug screenshot requires --out <file>"),
            )
            "tap" -> AppiumDebugAction.Tap(
                xRatio = xRatio ?: throw CliUsageException("debug tap requires --x-ratio"),
                yRatio = yRatio ?: throw CliUsageException("debug tap requires --y-ratio"),
            )
            "tap-element", "tapElement" -> AppiumDebugAction.TapElement(
                locator = LocatorDefinition(
                    strategy = strategy ?: throw CliUsageException("debug tap-element requires --strategy"),
                    value = locatorValue ?: throw CliUsageException("debug tap-element requires --locator"),
                ),
                xRatio = elementXRatio,
                yRatio = elementYRatio,
            )
            "input" -> AppiumDebugAction.Input(
                locator = LocatorDefinition(
                    strategy = strategy ?: throw CliUsageException("debug input requires --strategy"),
                    value = locatorValue ?: throw CliUsageException("debug input requires --locator"),
                ),
                text = text ?: throw CliUsageException("debug input requires --text"),
                clearFirst = clearFirst,
            )
            "tap-template", "tapTemplate" -> AppiumDebugAction.TapTemplate(
                template = template ?: throw CliUsageException("debug tap-template requires --template <file>"),
                threshold = threshold,
                scales = scales,
                roi = roi,
                targetXRatio = targetXRatio,
                targetYRatio = targetYRatio,
            )
            else -> throw CliUsageException("Unsupported debug action '$actionName'")
        }

        return CliCommand.Debug(
            planPath = planPath ?: throw CliUsageException("debug plan path is required"),
            action = action,
            keepInfrastructure = keepInfrastructure,
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

    private fun parseRatio(
        raw: String,
        option: String,
    ): Double {
        val value = raw.toDoubleOrNull()
            ?: throw CliUsageException("$option requires a number")
        if (value !in 0.0..1.0) {
            throw CliUsageException("$option must be between 0 and 1")
        }
        return value
    }

    private fun parseScales(raw: String): List<Double> {
        val values = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { value -> value.toDoubleOrNull() ?: throw CliUsageException("--scales requires comma-separated numbers") }
        if (values.isEmpty() || values.any { it <= 0.0 }) {
            throw CliUsageException("--scales requires positive comma-separated numbers")
        }
        return values
    }

    private fun parseRoi(raw: String): DebugRoi {
        val values = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { value -> value.toDoubleOrNull() ?: throw CliUsageException("--roi requires x,y,width,height numbers") }
        if (values.size != 4) {
            throw CliUsageException("--roi requires x,y,width,height")
        }
        return DebugRoi(values[0], values[1], values[2], values[3])
    }

    private fun parseBoolean(
        raw: String,
        option: String,
    ): Boolean {
        return when (raw.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> throw CliUsageException("$option requires true or false")
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
              soluna debug <plan.yaml> source [--out <file>] [--keep-infra]
              soluna debug <plan.yaml> screenshot --out <file> [--keep-infra]
              soluna debug <plan.yaml> tap --x-ratio <0..1> --y-ratio <0..1> [--keep-infra]
              soluna debug <plan.yaml> tap-element --strategy <strategy> --locator <value> [--element-x-ratio <0..1>] [--element-y-ratio <0..1>] [--keep-infra]
              soluna debug <plan.yaml> input --strategy <strategy> --locator <value> --text <text> [--clear-first true|false] [--keep-infra]
              soluna debug <plan.yaml> tap-template --template <png> [--roi x,y,w,h] [--threshold <0..1>] [--scales a,b,c] [--keep-infra]
              soluna debug <plan.yaml> shell [--keep-infra]

            Required:
              <plan.yaml>                Plan path. Device, artifacts, cases, elements, data, and fragments are resolved from the plan reference chain.

            Optional:
              --run-id <id>              Override generated run id.
              --param key=value          Runtime parameter override. Can be repeated.
              --report-root <dir>        Local report/artifact root. Default: build/soluna-runs.
              --expect passed|failed     Expected execution status. Default: passed.

            Debug:
              debug starts a temporary Appium/WDA session from the plan device/app config and runs low-level actions.
              shell keeps one session alive for step-by-step source/screenshot/tap/input/template debugging.
              It does not execute plan setup, cases, reports, uploads, or notifications.
              --roi uses normalized screenshot coordinates: x,y,width,height.
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

    data class Debug(
        val planPath: Path,
        val action: AppiumDebugAction,
        val keepInfrastructure: Boolean,
    ) : CliCommand()

    data class DebugShell(
        val planPath: Path,
        val keepInfrastructure: Boolean,
    ) : CliCommand()
}

private class CliUsageException(message: String) : IllegalArgumentException(message)
