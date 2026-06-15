package com.ugreen.iot.soluna.autotest.runner

import com.ugreen.iot.soluna.autotest.appium.action.defaultWebDriverActionExecutors
import com.ugreen.iot.soluna.autotest.appium.driver.AppiumJavaClientWebDriverAdapter
import com.ugreen.iot.soluna.autotest.appium.driver.AppiumSessionRequestFactory
import com.ugreen.iot.soluna.autotest.appium.driver.AppiumSessionRecoveryPolicy
import com.ugreen.iot.soluna.autotest.appium.driver.DriverSession
import com.ugreen.iot.soluna.autotest.appium.driver.RecoveringWebDriverAdapter
import com.ugreen.iot.soluna.autotest.appium.driver.WebDriverAdapter
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerHandle
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerManager
import com.ugreen.iot.soluna.autotest.appium.server.LocalProcessAppiumServerManager
import com.ugreen.iot.soluna.autotest.appium.wda.LocalGoIosWdaManager
import com.ugreen.iot.soluna.autotest.appium.wda.SolunaExtWdaBundleResolver
import com.ugreen.iot.soluna.autotest.appium.wda.WdaBundleResolveRequest
import com.ugreen.iot.soluna.autotest.appium.wda.WdaBundleResolver
import com.ugreen.iot.soluna.autotest.appium.wda.WdaHandle
import com.ugreen.iot.soluna.autotest.appium.wda.WdaManager
import com.ugreen.iot.soluna.autotest.artifact.ArtifactContentTypes
import com.ugreen.iot.soluna.autotest.artifact.ArtifactKind
import com.ugreen.iot.soluna.autotest.artifact.ArtifactStoreConfigDefinition
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadDrainResult
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadRequest
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploader
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadFailureNotifier
import com.ugreen.iot.soluna.autotest.artifact.DingTalkUploadFailureNotifier
import com.ugreen.iot.soluna.autotest.artifact.DefaultArtifactUploaderFactory
import com.ugreen.iot.soluna.autotest.artifact.LocalExplicitScreenshotSink
import com.ugreen.iot.soluna.autotest.artifact.NoOpArtifactUploadFailureNotifier
import com.ugreen.iot.soluna.autotest.artifact.PlanResourceManifestWriteResult
import com.ugreen.iot.soluna.autotest.artifact.PlanResourceManifestWriter
import com.ugreen.iot.soluna.autotest.artifact.FailureTraceScreenshotCollector
import com.ugreen.iot.soluna.autotest.artifact.PublishedTraceArtifact
import com.ugreen.iot.soluna.autotest.artifact.YamlArtifactStoreConfigParser
import com.ugreen.iot.soluna.autotest.config.DeviceConfigDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceConfigResolver
import com.ugreen.iot.soluna.autotest.config.YamlDeviceConfigParser
import com.ugreen.iot.soluna.autotest.config.toAppiumServerConfig
import com.ugreen.iot.soluna.autotest.config.toWdaConfig
import com.ugreen.iot.soluna.autotest.core.execution.ActionExecutor
import com.ugreen.iot.soluna.autotest.core.execution.DefaultActionExecutorRegistry
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionRequest
import com.ugreen.iot.soluna.autotest.core.execution.FailFastFailureStrategy
import com.ugreen.iot.soluna.autotest.core.execution.FailureStrategy
import com.ugreen.iot.soluna.autotest.core.execution.LinearExecutionEngine
import com.ugreen.iot.soluna.autotest.core.execution.NoRetryStrategy
import com.ugreen.iot.soluna.autotest.core.execution.PlanExecutionResult
import com.ugreen.iot.soluna.autotest.core.execution.RetryStrategy
import com.ugreen.iot.soluna.autotest.core.execution.ThreadSleeper
import com.ugreen.iot.soluna.autotest.core.hook.HookBus
import com.ugreen.iot.soluna.autotest.core.hook.SimpleHookBus
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.dsl.DslParser
import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
import com.ugreen.iot.soluna.autotest.report.ReportWriteResult
import com.ugreen.iot.soluna.autotest.report.ReportLinkRewriter
import com.ugreen.iot.soluna.autotest.report.ReportWriter
import com.ugreen.iot.soluna.autotest.notification.DefaultNotificationSenderFactory
import com.ugreen.iot.soluna.autotest.notification.NotificationMessage
import com.ugreen.iot.soluna.autotest.notification.NotificationSender
import com.ugreen.iot.soluna.autotest.notification.NotificationSendResult
import com.ugreen.iot.soluna.autotest.notification.NotificationSenderConfigDefinition
import com.ugreen.iot.soluna.autotest.notification.YamlNotificationSenderConfigParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.UUID

class PlanRunner(
    private val planParser: DslParser<PlanDefinition> = YamlPlanParser(),
    private val deviceConfigParser: DslParser<DeviceConfigDefinition> = YamlDeviceConfigParser(),
    private val artifactStoreConfigParser: DslParser<ArtifactStoreConfigDefinition> = YamlArtifactStoreConfigParser(),
    private val notificationSenderConfigParser: DslParser<NotificationSenderConfigDefinition> = YamlNotificationSenderConfigParser(),
    private val appiumServerManager: AppiumServerManager = LocalProcessAppiumServerManager(),
    private val wdaManager: WdaManager = LocalGoIosWdaManager(),
    private val wdaBundleResolver: WdaBundleResolver = SolunaExtWdaBundleResolver(),
    private val deviceConfigResolver: DeviceConfigResolver = DeviceConfigResolver(),
    private val webDriverAdapter: WebDriverAdapter = AppiumJavaClientWebDriverAdapter(),
    private val sessionRequestFactory: AppiumSessionRequestFactory = AppiumSessionRequestFactory(),
    private val hookBus: HookBus = SimpleHookBus(),
    private val failureStrategy: FailureStrategy = FailFastFailureStrategy,
    private val retryStrategy: RetryStrategy = NoRetryStrategy,
    private val sessionRecoveryPolicy: AppiumSessionRecoveryPolicy = AppiumSessionRecoveryPolicy(),
    private val referenceResolver: PlanReferenceResolver = PlanReferenceResolver(),
    private val defaultsResolver: PlanDefaultsResolver = PlanDefaultsResolver(),
    private val parameterResolver: PlanParameterResolver = PlanParameterResolver(),
    private val planResourceManifestWriter: PlanResourceManifestWriter = PlanResourceManifestWriter(),
    private val notificationSenderFactory: (NotificationSenderConfigDefinition) -> NotificationSender = { config ->
        DefaultNotificationSenderFactory.create(config)
    },
    private val artifactUploaderFactory: (ArtifactStoreConfigDefinition, ArtifactUploadFailureNotifier) -> ArtifactUploader = { config, notifier ->
        DefaultArtifactUploaderFactory.create(config, notifier)
    },
    private val actionExecutorFactory: (WebDriverAdapter, com.ugreen.iot.soluna.autotest.appium.action.ScreenshotSink) -> List<ActionExecutor> = { driver, screenshotSink ->
        defaultWebDriverActionExecutors(driver, screenshotSink)
    },
) {
    fun run(request: PlanRunRequest): PlanRunResult {
        val planPath = request.planPath.toAbsolutePath().normalize()
        val parsedPlan = planParser.parse(Files.readString(planPath))
        val deviceConfigPath = resolveDeviceConfigPath(planPath, parsedPlan)
        val parsedDeviceConfig = deviceConfigParser.parse(Files.readString(deviceConfigPath))
        val artifactStoreConfigPath = parsedPlan.artifactStore
            ?.let { artifactStorePath -> resolveReferencePath(planPath, artifactStorePath) }
        val artifactStoreConfig = artifactStoreConfigPath
            ?.let { artifactStorePath -> artifactStoreConfigParser.parse(Files.readString(artifactStorePath)) }
        val serverConfig = parsedDeviceConfig.appium.server.toAppiumServerConfig()
        val uploadFailureNotifier = artifactStoreConfig
            ?.let { config -> createUploadFailureNotifier(artifactStoreConfigPath, config) }
            ?: NoOpArtifactUploadFailureNotifier
        val lifecycleNotificationSenders = artifactStoreConfig
            ?.let { config -> createPlanLifecycleNotificationSenders(artifactStoreConfigPath, config) }
            ?: PlanLifecycleNotificationSenders()
        val artifactUploader = request.artifactUploader ?: artifactStoreConfig?.let { config ->
            artifactUploaderFactory(config, uploadFailureNotifier)
        }
        val ownsArtifactUploader = request.artifactUploader == null && artifactUploader != null
        val notificationResults = mutableListOf<NotificationSendResult>()
        lifecycleNotificationSenders.planStarted?.let { sender ->
            notificationResults += sender.send(parsedPlan.toPlanStartedMessage(request.runId, parsedDeviceConfig))
        }
        val screenshotSink = LocalExplicitScreenshotSink(
            outputDirectory = request.localArtifactRoot
                .resolve(request.runId)
                .resolve("resources")
                .toAbsolutePath()
                .normalize(),
        )
        var createdSession: DriverSession? = null
        var wdaHandle: WdaHandle? = null
        var recoveringDriver: RecoveringWebDriverAdapter? = null
        var runtimeDriver: WebDriverAdapter = webDriverAdapter
        var serverHandle: AppiumServerHandle? = null
        var testFinishedNotificationSent = false

        try {
            serverHandle = appiumServerManager.ensureRunning(serverConfig)
            val activeServerHandle = serverHandle
            val resolvedDeviceConfig = deviceConfigResolver.resolve(
                config = parsedDeviceConfig,
                appiumServerUrl = activeServerHandle.url,
            )
            val deviceConfig = resolveWdaBundleIfNeeded(
                deviceConfig = resolvedDeviceConfig,
                appiumServerUrl = activeServerHandle.url,
            )
            val assembledPlan = referenceResolver.resolve(
                plan = parsedPlan,
                planPath = planPath,
                platform = parsedPlan.app?.platform ?: deviceConfig.device.platform,
            )
            val planWithDefaults = defaultsResolver.resolve(assembledPlan)
            val plan = parameterResolver.resolve(
                plan = planWithDefaults,
                planPath = planPath,
                overrides = request.parameterOverrides,
            )
            wdaHandle = ensureWdaIfNeeded(
                deviceConfig = deviceConfig,
                request = request,
            )

            recoveringDriver = if (request.enableSessionRecovery && request.driverSessionId == null) {
                RecoveringWebDriverAdapter(
                    delegate = webDriverAdapter,
                    appiumServerManager = appiumServerManager,
                    serverConfig = serverConfig,
                    initialServerHandle = activeServerHandle,
                    recoveryPolicy = sessionRecoveryPolicy,
                )
            } else {
                null
            }
            runtimeDriver = recoveringDriver ?: webDriverAdapter
            val traceCollector = FailureTraceScreenshotCollector(
                driver = runtimeDriver,
                outputDirectory = request.localArtifactRoot
                    .resolve(request.runId)
                    .resolve("diagnostics")
                    .resolve("trace")
                    .toAbsolutePath()
                    .normalize(),
                config = plan.trace.screenshots,
                artifactUploader = artifactUploader,
            )

            val sessionId = request.driverSessionId ?: runtimeDriver.startSession(
                sessionRequestFactory.create(
                    serverUrl = activeServerHandle.url,
                    deviceConfig = deviceConfig,
                    plan = plan,
                    webDriverAgentUrl = wdaHandle?.url,
                ),
            ).also {
                createdSession = it
            }.sessionId

            val engine = LinearExecutionEngine(
                actionExecutorRegistry = DefaultActionExecutorRegistry(actionExecutorFactory(runtimeDriver, screenshotSink)),
                hookBus = hookBus,
                failureStrategy = failureStrategy,
                retryStrategy = retryStrategy,
                actionTraceCollector = traceCollector,
                sleeper = ThreadSleeper,
            )
            val executionResult = engine.execute(
                plan = plan,
                request = ExecutionRequest(
                    runId = request.runId,
                    driverSessionId = sessionId,
                ),
            )

            val result = PlanRunResult(
                plan = plan,
                deviceConfig = deviceConfig,
                serverHandle = recoveringDriver?.currentServerHandle() ?: activeServerHandle,
                driverSession = createdSession,
                executionResult = executionResult,
                wdaHandle = wdaHandle,
                traceArtifacts = traceCollector.published(),
            )
            lifecycleNotificationSenders.testFinished?.let { sender ->
                notificationResults += sender.send(result.toTestFinishedMessage())
            }
            testFinishedNotificationSent = true
            val report = request.reportWriter?.write(result)
            val reportWithManifest = report?.copy(
                resourceManifestFile = planResourceManifestWriter.write(
                    result = result,
                    directory = report.directory,
                    screenshots = screenshotSink.captured(),
                    artifactUploader = artifactUploader,
                ).also { manifest ->
                    artifactUploader?.let { uploader ->
                        publishReportArtifacts(
                            uploader = uploader,
                            runId = request.runId,
                            report = report,
                            manifest = manifest,
                        )
                    }
                }.file,
            )
            val uploadDrain = artifactUploader
                ?.takeIf { reportWithManifest != null }
                ?.drainRequired(artifactStoreConfig?.upload?.drainTimeoutMs ?: request.artifactDrainTimeoutMs)
            val finalResult = result.copy(
                report = reportWithManifest,
                artifactUploads = uploadDrain,
            )
            if (reportWithManifest != null) {
                lifecycleNotificationSenders.reportPublished?.let { sender ->
                    notificationResults += sender.send(finalResult.toReportPublishedMessage(artifactUploader))
                }
            }
            val resultWithNotifications = finalResult.copy(
                notifications = notificationResults.toList(),
            )
            val localCleanup = cleanupLocalArtifactsIfConfigured(
                plan = plan,
                result = resultWithNotifications,
                artifactUploader = artifactUploader,
                drainTimeoutMs = artifactStoreConfig?.upload?.drainTimeoutMs ?: request.artifactDrainTimeoutMs,
                defaultRunDirectory = request.localArtifactRoot.resolve(request.runId).toAbsolutePath().normalize(),
            )
            return resultWithNotifications.copy(
                localArtifactCleanup = localCleanup,
            )
        } catch (err: RuntimeException) {
            if (!testFinishedNotificationSent) {
                lifecycleNotificationSenders.testFinished?.let { sender ->
                    notificationResults += sender.send(parsedPlan.toTestFinishedErrorMessage(request.runId, parsedDeviceConfig, err))
                }
                testFinishedNotificationSent = true
            }
            throw err
        } finally {
            createdSession?.takeIf { request.stopDriverSessionAfterPlan }?.let {
                runtimeDriver.stopSession(it.sessionId)
            }
            wdaHandle?.takeIf { it.managed && request.stopManagedWdaAfterPlan }?.let {
                wdaManager.stop(it)
            }
            val finalServerHandle = recoveringDriver?.currentServerHandle() ?: serverHandle
            if (finalServerHandle?.managed == true && request.stopManagedServerAfterPlan) {
                appiumServerManager.stop(finalServerHandle)
            }
            if (ownsArtifactUploader) {
                artifactUploader.close()
            }
        }
    }

    private fun ensureWdaIfNeeded(
        deviceConfig: DeviceConfigDefinition,
        request: PlanRunRequest,
    ): WdaHandle? {
        if (request.driverSessionId != null) {
            return null
        }
        if (deviceConfig.device.platform?.equals("ios", ignoreCase = true) != true) {
            return null
        }
        if (!deviceConfig.ios.wda.enabled) {
            return null
        }
        return wdaManager.ensureRunning(deviceConfig.toWdaConfig())
    }

    private fun resolveWdaBundleIfNeeded(
        deviceConfig: DeviceConfigDefinition,
        appiumServerUrl: String,
    ): DeviceConfigDefinition {
        if (deviceConfig.device.platform?.equals("ios", ignoreCase = true) != true) {
            return deviceConfig
        }
        val wda = deviceConfig.ios.wda
        if (!wda.enabled || !wda.managed) {
            return deviceConfig
        }

        val resolution = wdaBundleResolver.resolve(
            WdaBundleResolveRequest(
                appiumServerUrl = appiumServerUrl,
                udid = deviceConfig.device.udid,
                bundleId = wda.bundleId,
                testRunnerBundleId = wda.testRunnerBundleId,
                xctestConfig = wda.xctestConfig,
            ),
        )
        val resolvedWda = wda.copy(
            bundleId = resolution.bundleId,
            testRunnerBundleId = resolution.testRunnerBundleId,
            xctestConfig = resolution.xctestConfig,
        )
        return deviceConfig.copy(
            ios = deviceConfig.ios.copy(
                wda = resolvedWda,
            ),
        )
    }

    private fun cleanupLocalArtifactsIfConfigured(
        plan: PlanDefinition,
        result: PlanRunResult,
        artifactUploader: ArtifactUploader?,
        drainTimeoutMs: Long,
        defaultRunDirectory: Path,
    ): LocalArtifactCleanupResult? {
        if (plan.localArtifacts.cleanup.mode != "after-upload-success") {
            return null
        }
        val reportRunDirectory = result.report?.directory?.parent?.toAbsolutePath()?.normalize()
        val runDirectories = listOfNotNull(reportRunDirectory, defaultRunDirectory)
            .distinct()
            .filter { it.fileName?.toString() == result.executionResult.runId }
        if (artifactUploader == null) {
            return LocalArtifactCleanupResult(
                attempted = true,
                deleted = false,
                directories = runDirectories,
                reason = "artifact uploader is not enabled",
            )
        }
        val drain = artifactUploader.drainAll(drainTimeoutMs)
        if (!drain.completed || drain.failedCount > 0 || drain.abandonedCount > 0) {
            return LocalArtifactCleanupResult(
                attempted = true,
                deleted = false,
                directories = runDirectories,
                reason = "artifact uploads are not fully completed",
            )
        }
        runDirectories.forEach { directory ->
            if (Files.exists(directory)) {
                Files.walk(directory).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
        return LocalArtifactCleanupResult(
            attempted = true,
            deleted = true,
            directories = runDirectories,
            reason = null,
        )
    }

    private fun resolveDeviceConfigPath(
        planPath: Path,
        plan: PlanDefinition,
    ): Path {
        val baseDir = planPath.parent ?: Path.of(".").toAbsolutePath().normalize()
        val configuredPath = plan.deviceConfig?.let { baseDir.resolve(it) }
            ?: error("Plan '${plan.id}' does not define deviceConfig")
        return if (configuredPath.isAbsolute) {
            configuredPath.normalize()
        } else {
            baseDir.resolve(configuredPath).normalize()
        }
    }

    private fun resolveReferencePath(
        basePath: Path,
        reference: String,
    ): Path {
        val baseDir = basePath.parent ?: Path.of(".").toAbsolutePath().normalize()
        val configuredPath = Path.of(reference)
        return if (configuredPath.isAbsolute) {
            configuredPath.normalize()
        } else {
            baseDir.resolve(configuredPath).normalize()
        }
    }

    private fun publishReportArtifacts(
        uploader: ArtifactUploader,
        runId: String,
        report: ReportWriteResult,
        manifest: PlanResourceManifestWriteResult,
    ) {
        val dataObjectKey = uploader.objectKey(runId, ArtifactKind.REPORT, report.dataFile.fileName.toString())
        val manifestObjectKey = uploader.objectKey(runId, ArtifactKind.REPORT, manifest.file.fileName.toString())
        val htmlObjectKey = uploader.objectKey(runId, ArtifactKind.REPORT, report.htmlFile.fileName.toString())
        ReportLinkRewriter.rewrite(
            htmlFile = report.htmlFile,
            links = mapOf(
                report.dataFile.fileName.toString() to uploader.urlFor(dataObjectKey),
                manifest.file.fileName.toString() to uploader.urlFor(manifestObjectKey),
            ),
        )

        manifest.resources.forEach { resource ->
            val objectKey = resource.objectKey ?: return@forEach
            uploader.enqueue(
                ArtifactUploadRequest(
                    localPath = resource.localPath,
                    objectKey = objectKey,
                    contentType = resource.contentType,
                    requiredForReport = true,
                    compress = false,
                    metadata = mapOf(
                        "resourceId" to resource.resourceId,
                        "purpose" to resource.purpose,
                    ),
                ),
            )
        }
        uploader.enqueue(
            ArtifactUploadRequest(
                localPath = report.dataFile,
                objectKey = dataObjectKey,
                contentType = ArtifactContentTypes.forFileName(report.dataFile.fileName.toString()),
                requiredForReport = true,
            ),
        )
        uploader.enqueue(
            ArtifactUploadRequest(
                localPath = manifest.file,
                objectKey = manifestObjectKey,
                contentType = ArtifactContentTypes.forFileName(manifest.file.fileName.toString()),
                requiredForReport = true,
            ),
        )
        uploader.enqueue(
            ArtifactUploadRequest(
                localPath = report.htmlFile,
                objectKey = htmlObjectKey,
                contentType = ArtifactContentTypes.forFileName(report.htmlFile.fileName.toString()),
                requiredForReport = true,
            ),
        )
    }

    private fun createUploadFailureNotifier(
        artifactStoreConfigPath: Path?,
        artifactStoreConfig: ArtifactStoreConfigDefinition,
    ): ArtifactUploadFailureNotifier {
        val uploadFailuresRef = artifactStoreConfig.notifications.uploadFailures ?: return NoOpArtifactUploadFailureNotifier
        val artifactConfigPath = artifactStoreConfigPath ?: return NoOpArtifactUploadFailureNotifier
        val notificationConfigPath = resolveReferencePath(
            basePath = artifactConfigPath,
            reference = uploadFailuresRef,
        )
        val notificationConfig = notificationSenderConfigParser.parse(Files.readString(notificationConfigPath))
        return DingTalkUploadFailureNotifier(
            sender = notificationSenderFactory(notificationConfig),
            policy = notificationConfig.uploadFailurePolicy,
        )
    }

    private fun createPlanLifecycleNotificationSenders(
        artifactStoreConfigPath: Path?,
        artifactStoreConfig: ArtifactStoreConfigDefinition,
    ): PlanLifecycleNotificationSenders {
        return PlanLifecycleNotificationSenders(
            planStarted = createNotificationSender(artifactStoreConfigPath, artifactStoreConfig.notifications.planStarted),
            testFinished = createNotificationSender(artifactStoreConfigPath, artifactStoreConfig.notifications.testFinished),
            reportPublished = createNotificationSender(
                artifactStoreConfigPath = artifactStoreConfigPath,
                notificationRef = artifactStoreConfig.notifications.reportPublished
                    ?: artifactStoreConfig.notifications.planFinished,
            ),
        )
    }

    private fun createNotificationSender(
        artifactStoreConfigPath: Path?,
        notificationRef: String?,
    ): NotificationSender? {
        val ref = notificationRef ?: return null
        val artifactConfigPath = artifactStoreConfigPath ?: return null
        val notificationConfigPath = resolveReferencePath(
            basePath = artifactConfigPath,
            reference = ref,
        )
        val notificationConfig = notificationSenderConfigParser.parse(Files.readString(notificationConfigPath))
        return notificationSenderFactory(notificationConfig)
    }

    private fun PlanDefinition.toPlanStartedMessage(
        runId: String,
        deviceConfig: DeviceConfigDefinition,
    ): NotificationMessage {
        return NotificationMessage(
            title = "Soluna plan started",
            markdown = """
                ### Soluna plan started
                - Plan: `$name` (`$id`)
                - Run: `$runId`
                - Device: `${deviceConfig.id}`
                - Platform: `${deviceConfig.device.platform ?: "-"}`
            """.trimIndent(),
        )
    }

    private fun PlanRunResult.toTestFinishedMessage(): NotificationMessage {
        return NotificationMessage(
            title = "Soluna test finished: ${executionResult.status.name.lowercase()}",
            markdown = """
                ### Soluna test finished
                - Plan: `${plan.name}` (`${plan.id}`)
                - Run: `${executionResult.runId}`
                - Status: `${executionResult.status.name.lowercase()}`
                - Device: `${deviceConfig.id}`
                - Stages: `${executionResult.stages.size}`
            """.trimIndent(),
        )
    }

    private fun PlanDefinition.toTestFinishedErrorMessage(
        runId: String,
        deviceConfig: DeviceConfigDefinition,
        err: RuntimeException,
    ): NotificationMessage {
        return NotificationMessage(
            title = "Soluna test finished: error",
            markdown = """
                ### Soluna test finished
                - Plan: `$name` (`$id`)
                - Run: `$runId`
                - Status: `error`
                - Device: `${deviceConfig.id}`
                - Error: `${err.message ?: err::class.simpleName ?: "unknown error"}`
            """.trimIndent(),
        )
    }

    private fun PlanRunResult.toReportPublishedMessage(
        artifactUploader: ArtifactUploader?,
    ): NotificationMessage {
        val runId = executionResult.runId
        val reportUrl = report?.htmlFile?.fileName?.toString()
            ?.let { fileName -> artifactUploader?.urlFor(artifactUploader.objectKey(runId, ArtifactKind.REPORT, fileName)) }
            ?: report?.htmlFile?.toAbsolutePath()?.normalize()?.toString()
            ?: "-"
        val manifestUrl = report?.resourceManifestFile?.fileName?.toString()
            ?.let { fileName -> artifactUploader?.urlFor(artifactUploader.objectKey(runId, ArtifactKind.REPORT, fileName)) }
            ?: report?.resourceManifestFile?.toAbsolutePath()?.normalize()?.toString()
            ?: "-"
        val uploadSummary = artifactUploads?.let { uploads ->
            "completed=${uploads.completed}, uploaded=${uploads.uploadedCount}, failed=${uploads.failedCount}, abandoned=${uploads.abandonedCount}"
        } ?: "not enabled"
        return NotificationMessage(
            title = "Soluna report published: ${executionResult.status.name.lowercase()}",
            markdown = """
                ### Soluna report published
                - Plan: `${plan.name}` (`${plan.id}`)
                - Run: `$runId`
                - Status: `${executionResult.status.name.lowercase()}`
                - Device: `${deviceConfig.id}`
                - Upload: `$uploadSummary`
                - Report: [index.html]($reportUrl)
                - Manifest: [plan-resource-manifest.json]($manifestUrl)
            """.trimIndent(),
        )
    }
}

private data class PlanLifecycleNotificationSenders(
    val planStarted: NotificationSender? = null,
    val testFinished: NotificationSender? = null,
    val reportPublished: NotificationSender? = null,
)

data class PlanRunRequest(
    val planPath: Path,
    val runId: String = UUID.randomUUID().toString(),
    val driverSessionId: String? = null,
    val parameterOverrides: Map<String, com.fasterxml.jackson.databind.JsonNode> = emptyMap(),
    val reportWriter: ReportWriter? = null,
    val artifactUploader: ArtifactUploader? = null,
    val artifactDrainTimeoutMs: Long = 60_000,
    val localArtifactRoot: Path = Path.of("build/soluna-runs"),
    val enableSessionRecovery: Boolean = true,
    val stopDriverSessionAfterPlan: Boolean = true,
    val stopManagedServerAfterPlan: Boolean = true,
    val stopManagedWdaAfterPlan: Boolean = true,
)

data class PlanRunResult(
    val plan: PlanDefinition,
    val deviceConfig: DeviceConfigDefinition,
    val serverHandle: AppiumServerHandle,
    val driverSession: DriverSession?,
    val executionResult: PlanExecutionResult,
    val wdaHandle: WdaHandle? = null,
    val report: ReportWriteResult? = null,
    val traceArtifacts: List<PublishedTraceArtifact> = emptyList(),
    val artifactUploads: ArtifactUploadDrainResult? = null,
    val notifications: List<NotificationSendResult> = emptyList(),
    val localArtifactCleanup: LocalArtifactCleanupResult? = null,
)

data class LocalArtifactCleanupResult(
    val attempted: Boolean,
    val deleted: Boolean,
    val directories: List<Path>,
    val reason: String?,
)
