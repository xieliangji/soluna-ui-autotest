package com.soluna.ui.autotest.runner

import com.soluna.ui.autotest.appium.action.defaultWebDriverActionExecutors
import com.soluna.ui.autotest.appium.driver.AppiumJavaClientWebDriverAdapter
import com.soluna.ui.autotest.appium.driver.AppiumSessionRequestFactory
import com.soluna.ui.autotest.appium.driver.AppiumSessionRecoveryPolicy
import com.soluna.ui.autotest.appium.driver.DriverSession
import com.soluna.ui.autotest.appium.driver.RecoveringWebDriverAdapter
import com.soluna.ui.autotest.appium.driver.WebDriverAdapter
import com.soluna.ui.autotest.appium.server.AppiumServerHandle
import com.soluna.ui.autotest.appium.server.AppiumServerManager
import com.soluna.ui.autotest.appium.server.LocalProcessAppiumServerManager
import com.soluna.ui.autotest.appium.wda.LocalGoIosWdaManager
import com.soluna.ui.autotest.appium.wda.SolunaExtWdaBundleResolver
import com.soluna.ui.autotest.appium.wda.WdaBundleResolveRequest
import com.soluna.ui.autotest.appium.wda.WdaBundleResolver
import com.soluna.ui.autotest.appium.wda.WdaConfig
import com.soluna.ui.autotest.appium.wda.WdaHandle
import com.soluna.ui.autotest.appium.wda.WdaManager
import com.soluna.ui.autotest.appium.action.PlanResourceSink
import com.soluna.ui.autotest.artifact.ArtifactContentTypes
import com.soluna.ui.autotest.artifact.ArtifactKind
import com.soluna.ui.autotest.artifact.ArtifactStoreConfigDefinition
import com.soluna.ui.autotest.artifact.ArtifactUploadDrainResult
import com.soluna.ui.autotest.artifact.ArtifactUploadRequest
import com.soluna.ui.autotest.artifact.ArtifactUploader
import com.soluna.ui.autotest.artifact.ArtifactUploadFailureNotifier
import com.soluna.ui.autotest.artifact.DingTalkUploadFailureNotifier
import com.soluna.ui.autotest.artifact.DefaultArtifactUploaderFactory
import com.soluna.ui.autotest.artifact.LocalExplicitScreenshotSink
import com.soluna.ui.autotest.artifact.NoOpArtifactUploadFailureNotifier
import com.soluna.ui.autotest.artifact.PlanResourceManifestWriteResult
import com.soluna.ui.autotest.artifact.PlanResourceManifestWriter
import com.soluna.ui.autotest.artifact.FailureTraceScreenshotCollector
import com.soluna.ui.autotest.artifact.PublishedTraceArtifact
import com.soluna.ui.autotest.artifact.YamlArtifactStoreConfigParser
import com.soluna.ui.autotest.config.AppMetadataResolver
import com.soluna.ui.autotest.config.DeviceConfigDefinition
import com.soluna.ui.autotest.config.DeviceConfigResolver
import com.soluna.ui.autotest.config.YamlDeviceConfigParser
import com.soluna.ui.autotest.config.toAppiumServerConfig
import com.soluna.ui.autotest.config.toWdaConfig
import com.soluna.ui.autotest.core.execution.ActionExecutor
import com.soluna.ui.autotest.core.execution.ActionExecutionResult
import com.soluna.ui.autotest.core.execution.ContinueCaseFailureStrategy
import com.soluna.ui.autotest.core.execution.DefaultActionExecutorRegistry
import com.soluna.ui.autotest.core.execution.ExecutionRequest
import com.soluna.ui.autotest.core.execution.FailFastFailureStrategy
import com.soluna.ui.autotest.core.execution.FailureStrategy
import com.soluna.ui.autotest.core.execution.LinearExecutionEngine
import com.soluna.ui.autotest.core.execution.NoRetryStrategy
import com.soluna.ui.autotest.core.execution.PlanExecutionResult
import com.soluna.ui.autotest.core.execution.RetryStrategy
import com.soluna.ui.autotest.core.execution.ThreadSleeper
import com.soluna.ui.autotest.core.hook.HookBus
import com.soluna.ui.autotest.core.hook.DefaultLoggingHook
import com.soluna.ui.autotest.core.hook.SimpleHookBus
import com.soluna.ui.autotest.core.hook.Slf4jExecutionLogger
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.dsl.DslParser
import com.soluna.ui.autotest.dsl.YamlPlanParser
import com.soluna.ui.autotest.report.ReportWriteResult
import com.soluna.ui.autotest.report.ReportLinkRewriter
import com.soluna.ui.autotest.report.ReportWriter
import com.soluna.ui.autotest.report.ExecutionFailureSummary
import com.soluna.ui.autotest.report.ExecutionReportSummaries
import com.soluna.ui.autotest.report.ExecutionReportSummary
import com.soluna.ui.autotest.notification.DefaultNotificationSenderFactory
import com.soluna.ui.autotest.notification.NotificationMessage
import com.soluna.ui.autotest.notification.NotificationSender
import com.soluna.ui.autotest.notification.NotificationSendResult
import com.soluna.ui.autotest.notification.NotificationSenderConfigDefinition
import com.soluna.ui.autotest.notification.YamlNotificationSenderConfigParser
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Comparator
import java.util.UUID

private const val APP_UI_AUTOTEST_TITLE = "App UI自动化测试"

class PlanRunner(
    private val planParser: DslParser<PlanDefinition> = YamlPlanParser(),
    private val deviceConfigParser: DslParser<DeviceConfigDefinition> = YamlDeviceConfigParser(),
    private val artifactStoreConfigParser: DslParser<ArtifactStoreConfigDefinition> = YamlArtifactStoreConfigParser(),
    private val notificationSenderConfigParser: DslParser<NotificationSenderConfigDefinition> = YamlNotificationSenderConfigParser(),
    private val appiumServerManager: AppiumServerManager = LocalProcessAppiumServerManager(),
    private val wdaManager: WdaManager = LocalGoIosWdaManager(),
    private val wdaBundleResolver: WdaBundleResolver = SolunaExtWdaBundleResolver(),
    private val deviceConfigResolver: DeviceConfigResolver = DeviceConfigResolver(),
    private val appMetadataResolver: AppMetadataResolver = AppMetadataResolver(),
    private val webDriverAdapter: WebDriverAdapter = AppiumJavaClientWebDriverAdapter(),
    private val sessionRequestFactory: AppiumSessionRequestFactory = AppiumSessionRequestFactory(),
    private val hookBus: HookBus = defaultHookBus(),
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
    private val actionExecutorFactory: (WebDriverAdapter, PlanResourceSink) -> List<ActionExecutor> = { driver, resourceSink ->
        defaultWebDriverActionExecutors(driver, resourceSink)
    },
    private val clock: () -> Instant = { Instant.now() },
    private val notificationZoneId: ZoneId = ZoneId.systemDefault(),
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
            ).let { resolvedPlan ->
                appMetadataResolver.resolve(
                    plan = resolvedPlan,
                    deviceConfig = deviceConfig,
                    appiumServerUrl = activeServerHandle.url,
                )
            }
            lifecycleNotificationSenders.planStarted?.let { sender ->
                notificationResults += sender.send(plan.toPlanStartedMessage(request.runId, deviceConfig, clock()))
            }
            val wdaConfig = wdaConfigIfNeeded(
                deviceConfig = deviceConfig,
                request = request,
            )
            wdaHandle = ensureWdaIfNeeded(
                wdaConfig = wdaConfig,
            )

            recoveringDriver = if (request.enableSessionRecovery && request.driverSessionId == null) {
                RecoveringWebDriverAdapter(
                    delegate = webDriverAdapter,
                    appiumServerManager = appiumServerManager,
                    serverConfig = serverConfig,
                    initialServerHandle = activeServerHandle,
                    wdaManager = wdaManager,
                    wdaConfig = wdaConfig,
                    initialWdaHandle = wdaHandle,
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
                failureStrategy = resolveFailureStrategy(plan.defaults.failureStrategy),
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
                notificationResults += sender.send(result.toTestFinishedMessage(clock()))
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
                    notificationResults += sender.send(finalResult.toReportPublishedMessage(artifactUploader, clock()))
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
                    notificationResults += sender.send(
                        parsedPlan.toTestFinishedErrorMessage(request.runId, parsedDeviceConfig, err, clock()),
                    )
                }
                testFinishedNotificationSent = true
            }
            throw err
        } finally {
            createdSession?.takeIf { request.stopDriverSessionAfterPlan }?.let {
                runtimeDriver.stopSession(it.sessionId)
            }
            val finalWdaHandle = recoveringDriver?.currentWdaHandle() ?: wdaHandle
            finalWdaHandle?.takeIf { it.managed && request.stopManagedWdaAfterPlan }?.let {
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
        wdaConfig: WdaConfig?,
    ): WdaHandle? {
        return wdaConfig?.let { wdaManager.ensureRunning(it) }
    }

    private fun wdaConfigIfNeeded(
        deviceConfig: DeviceConfigDefinition,
        request: PlanRunRequest,
    ): WdaConfig? {
        if (request.driverSessionId != null) {
            return null
        }
        if (deviceConfig.device.platform?.equals("ios", ignoreCase = true) != true) {
            return null
        }
        if (!deviceConfig.ios.wda.enabled) {
            return null
        }
        return deviceConfig.toWdaConfig().copy(
            logDirectory = request.localArtifactRoot
                .resolve(request.runId)
                .resolve("diagnostics")
                .resolve("wda")
                .toAbsolutePath()
                .normalize(),
        )
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

    private fun resolveFailureStrategy(name: String?): FailureStrategy {
        return when (name?.trim()?.lowercase()) {
            null, "" -> failureStrategy
            "stop-case", "fail-fast" -> FailFastFailureStrategy
            "continue-case" -> ContinueCaseFailureStrategy
            else -> error("Unsupported failureStrategy '$name'")
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
        sentAt: Instant,
    ): NotificationMessage {
        val plannedCaseCount = stages.sumOf { stage -> stage.cases.size + stage.caseRefs.size }
        return NotificationMessage(
            title = APP_UI_AUTOTEST_TITLE,
            markdown = notificationMarkdown(
                notificationItem("设备名称", deviceConfig.deviceDisplayName()),
                notificationItem("设备标识", deviceConfig.deviceIdentifier()),
                notificationItem("应用名称", appDisplayName()),
                notificationItem("应用标识", app?.id ?: "-"),
                notificationItem("产品型号", productModel),
                notificationItem("运行编号", runId),
                notificationItem("开始时间", formatNotificationTime(sentAt)),
                notificationItem("平台类型", app?.platform ?: deviceConfig.device.platform ?: "-"),
                notificationItem("计划阶段", stages.size.toString()),
                notificationItem("计划用例", plannedCaseCount.toString()),
            ),
        )
    }

    private fun PlanRunResult.toTestFinishedMessage(sentAt: Instant): NotificationMessage {
        val summary = ExecutionReportSummaries.summary(executionResult)
        val failures = ExecutionReportSummaries.failures(executionResult, limit = 3)
        return NotificationMessage(
            title = APP_UI_AUTOTEST_TITLE,
            markdown = plan.notificationMarkdown(
                notificationItem("设备名称", deviceConfig.deviceDisplayName()),
                notificationItem("设备标识", deviceConfig.deviceIdentifier()),
                notificationItem("应用名称", plan.appDisplayName()),
                notificationItem("应用标识", plan.app?.id ?: "-"),
                notificationItem("产品型号", plan.productModel),
                notificationItem("运行编号", executionResult.runId),
                notificationItem("开始时间", formatOptionalNotificationTime(executionStartedAt())),
                notificationItem("结束时间", formatNotificationTime(executionFinishedAt() ?: sentAt)),
                notificationItem("执行状态", statusText(executionResult.status.name.lowercase())),
                notificationItem("平台类型", plan.app?.platform ?: deviceConfig.device.platform ?: "-"),
                notificationItem("用例结果", summary.caseResultText()),
                notificationItem("动作结果", summary.actionResultText()),
                notificationItem("追踪资源", traceArtifacts.size.toString()),
                notificationItem("失败摘要", failures.failureSummaryText()),
            ),
        )
    }

    private fun PlanDefinition.toTestFinishedErrorMessage(
        runId: String,
        deviceConfig: DeviceConfigDefinition,
        err: RuntimeException,
        sentAt: Instant,
    ): NotificationMessage {
        return NotificationMessage(
            title = APP_UI_AUTOTEST_TITLE,
            markdown = notificationMarkdown(
                notificationItem("设备名称", deviceConfig.deviceDisplayName()),
                notificationItem("设备标识", deviceConfig.deviceIdentifier()),
                notificationItem("应用名称", appDisplayName()),
                notificationItem("应用标识", app?.id ?: "-"),
                notificationItem("产品型号", productModel),
                notificationItem("运行编号", runId),
                notificationItem("开始时间", "-"),
                notificationItem("结束时间", formatNotificationTime(sentAt)),
                notificationItem("执行状态", "异常"),
                notificationItem("平台类型", app?.platform ?: deviceConfig.device.platform ?: "-"),
                notificationItem("失败摘要", err.message ?: err::class.simpleName ?: "unknown error"),
            ),
        )
    }

    private fun PlanRunResult.toReportPublishedMessage(
        artifactUploader: ArtifactUploader?,
        sentAt: Instant,
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
            val state = if (uploads.completed) "已完成" else "未完成"
            "$state，${uploads.uploadedCount} 成功，${uploads.failedCount} 失败，${uploads.abandonedCount} 放弃"
        } ?: "未启用"
        val summary = ExecutionReportSummaries.summary(executionResult)
        val failures = ExecutionReportSummaries.failures(executionResult, limit = 3)
        return NotificationMessage(
            title = APP_UI_AUTOTEST_TITLE,
            markdown = plan.notificationMarkdown(
                notificationItem("设备名称", deviceConfig.deviceDisplayName()),
                notificationItem("设备标识", deviceConfig.deviceIdentifier()),
                notificationItem("应用名称", plan.appDisplayName()),
                notificationItem("应用标识", plan.app?.id ?: "-"),
                notificationItem("产品型号", plan.productModel),
                notificationItem("运行编号", runId),
                notificationItem("开始时间", formatOptionalNotificationTime(executionStartedAt())),
                notificationItem("结束时间", formatNotificationTime(executionFinishedAt() ?: sentAt)),
                notificationItem("执行状态", statusText(executionResult.status.name.lowercase())),
                notificationItem("用例结果", summary.caseResultText()),
                notificationItem("动作结果", summary.actionResultText()),
                notificationItem("失败摘要", failures.failureSummaryText()),
                notificationItem("上传状态", uploadSummary),
                notificationLinkItem("报告链接", "index.html", reportUrl),
                notificationLinkItem("资源清单", "plan-resource-manifest.json", manifestUrl),
            ),
        )
    }

    private fun PlanDefinition.notificationMarkdown(vararg items: String): String {
        val itemLines = items.joinToString(separator = "\n") { "- $it" }
        return buildString {
            appendLine("""<font color="#00543F" size="4">**$APP_UI_AUTOTEST_TITLE**</font>""")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("""> <font color="#1AB66A" size="4">${mdText(productModel)} UI 自动化测试</font>""")
            appendLine()
            appendLine("---")
            appendLine()
            append(itemLines)
        }
    }

    private fun PlanDefinition.appDisplayName(): String {
        return app?.name?.takeIf { it.isNotBlank() } ?: productModel
    }

    private fun notificationItem(
        label: String,
        value: String,
    ): String {
        return "**$label:** ${mdText(value)}"
    }

    private fun notificationLinkItem(
        label: String,
        text: String,
        url: String,
    ): String {
        return "**$label:** [${mdText(text)}]($url)"
    }

    private fun DeviceConfigDefinition.deviceDisplayName(): String {
        return device.name?.takeIf { it.isNotBlank() } ?: device.udid
    }

    private fun DeviceConfigDefinition.deviceIdentifier(): String {
        return device.udid
    }

    private fun PlanRunResult.executionStartedAt(): Instant? {
        return executionResult.flattenActions()
            .mapNotNull { it.startedAt.toInstantOrNull() }
            .minOrNull()
    }

    private fun PlanRunResult.executionFinishedAt(): Instant? {
        return executionResult.flattenActions()
            .mapNotNull { it.finishedAt.toInstantOrNull() }
            .maxOrNull()
    }

    private fun PlanExecutionResult.flattenActions(): List<ActionExecutionResult> {
        return buildList {
            addAll(setupActions)
            stages.forEach { stage ->
                addAll(stage.setupActions)
                stage.cases.forEach { case ->
                    addAll(case.setupActions)
                    addAll(case.actions)
                    addAll(case.teardownActions)
                }
                addAll(stage.teardownActions)
            }
            addAll(teardownActions)
        }
    }

    private fun ExecutionReportSummary.caseResultText(): String {
        return "${casePassed}/${caseTotal} 通过，${caseFailed} 失败，${caseSkipped} 跳过"
    }

    private fun ExecutionReportSummary.actionResultText(): String {
        return "${actionPassed}/${actionTotal} 通过，${actionFailed} 失败，${actionSkipped} 跳过"
    }

    private fun List<ExecutionFailureSummary>.failureSummaryText(): String {
        if (isEmpty()) {
            return "无"
        }
        return joinToString(separator = "；") { failure ->
            val location = listOfNotNull(failure.stageId, failure.caseId)
                .joinToString("/")
                .ifBlank { "plan" }
            val action = listOfNotNull(failure.actionKeyword, failure.actionId?.let { "#$it" })
                .joinToString(" ")
                .ifBlank { "-" }
            val reason = failure.error ?: failure.message ?: "-"
            "$location ${phaseText(failure.phase)} #${failure.index} $action $reason"
        }.let { value -> value.take(300) }
    }

    private fun phaseText(phase: String): String {
        return when (phase) {
            "plan.setup" -> "计划前置"
            "plan.teardown" -> "计划后置"
            "stage.setup" -> "阶段前置"
            "stage.teardown" -> "阶段后置"
            "case.setup" -> "用例前置"
            "case.action" -> "用例步骤"
            "case.teardown" -> "用例后置"
            else -> phase
        }
    }

    private fun statusText(status: String): String {
        return when (status) {
            "passed" -> "通过"
            "failed" -> "失败"
            "skipped" -> "跳过"
            "running" -> "运行中"
            else -> status
        }
    }

    private fun formatNotificationTime(instant: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(notificationZoneId)
            .format(instant)
    }

    private fun formatOptionalNotificationTime(instant: Instant?): String {
        return instant?.let { formatNotificationTime(it) } ?: "-"
    }

    private fun String?.toInstantOrNull(): Instant? {
        val value = this?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun mdText(value: String): String {
        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .ifBlank { "-" }
    }
}

private fun defaultHookBus(): HookBus {
    return SimpleHookBus(
        listOf(
            DefaultLoggingHook(Slf4jExecutionLogger),
        ),
    )
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
