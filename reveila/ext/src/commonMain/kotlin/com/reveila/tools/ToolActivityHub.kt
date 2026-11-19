package com.reveila.tools

import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.platform.PlatformSystem
import com.reveila.tools.browser.WebPage
import com.reveila.tools.download.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BrowserElementSummary(
    val index: Int,
    val tagName: String,
    val type: String = "",
    val name: String = "",
    val elementId: String = "",
    val text: String = "",
    val placeholder: String = "",
    val value: String = "",
    val href: String = "",
    val isClickable: Boolean = false,
    val isInputField: Boolean = false,
    val isSecret: Boolean = false,
    val formId: String = ""
)

@Serializable
data class BrowserLiveActivity(
    val isVisible: Boolean = false,
    val url: String = "",
    val title: String = "",
    val statusCode: Int = 0,
    val lastAction: String = "",
    val contentPreview: String = "",
    val rawHtml: String = "",
    val elements: List<BrowserElementSummary> = emptyList(),
    val controlOwner: String = "AGENT", // "AGENT" | "USER"
    val waitingForUser: Boolean = false,
    val takeoverReason: String = "",
    val takeoverMessage: String = "",
    val updatedAtMs: Long = 0L
)

@Serializable
data class TerminalLiveActivity(
    val isVisible: Boolean = false,
    val executionId: String = "default",
    val shellName: String = "",
    val workingDir: String = "",
    val currentScript: String = "",
    val outputText: String = "",
    val exitCode: Int? = null,
    val status: String = "IDLE", // "IDLE" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED"
    val controlOwner: String = "AGENT", // "AGENT" | "USER"
    val waitingForUser: Boolean = false,
    val updatedAtMs: Long = 0L
)

@Serializable
data class DownloadActivityEntry(
    val downloadId: String,
    val fileName: String,
    val url: String = "",
    val destinationPath: String = "",
    val status: String = "IDLE",
    val percentage: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val message: String = "",
    val updatedAtMs: Long = 0L
)

@Serializable
data class DownloadLiveActivity(
    val isVisible: Boolean = false,
    val controlOwner: String = "AGENT", // "AGENT" | "USER"
    val availableStorageBytes: Long = -1L,
    val items: List<DownloadActivityEntry> = emptyList(),
    val updatedAtMs: Long = 0L
)

@Serializable
data class PendingToolAuthorizationView(
    val requestId: String,
    val traceId: String = "",
    val sessionId: String = "",
    val spaceId: String = "main-space",
    val toolName: String,
    val action: String = "",
    val scope: String = "*",
    val title: String = "",
    val reason: String = "",
    val commandPreview: String = "",
    val riskLevel: String = "MEDIUM",
    val requiresCredentials: Boolean = false,
    val credentialType: String = "NONE", // "NONE" | "WEBSITE_LOGIN" | "SECRET_KEY"
    val credentialDomain: String = "",
    val credentialKey: String = "",
    val createdAtMs: Long = 0L
)

@Serializable
data class ToolActivitySnapshot(
    val browser: BrowserLiveActivity = BrowserLiveActivity(),
    val terminal: TerminalLiveActivity = TerminalLiveActivity(),
    val downloads: DownloadLiveActivity = DownloadLiveActivity(),
    val pendingAuthorization: PendingToolAuthorizationView? = null,
    val waitingForUserInput: Boolean = false
)

/**
 * Reactive central hub tracking live activity across Browser, Terminal, Downloads,
 * and pending Human-In-The-Loop tool authorizations so UI layers can render live mini-cards,
 * waiting hints, and interactive user takeover windows.
 */
object ToolActivityHub {

    private val lock = PlatformLock()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var currentSnapshot = ToolActivitySnapshot()
    private val _snapshotFlow = MutableStateFlow(currentSnapshot)
    val snapshotFlow: StateFlow<ToolActivitySnapshot> = _snapshotFlow.asStateFlow()

    fun getSnapshot(): ToolActivitySnapshot = lock.withLock { currentSnapshot }

    fun getSnapshotJson(): String = json.encodeToString(getSnapshot())

    fun resetForTest() {
        lock.withLock {
            currentSnapshot = ToolActivitySnapshot()
            _snapshotFlow.value = currentSnapshot
        }
    }

    fun updateBrowserPage(
        page: WebPage?,
        actionDescription: String,
        controlOwner: String? = null,
        waitingForUser: Boolean? = null,
        takeoverReason: String? = null,
        takeoverMessage: String? = null
    ) {
        val now = PlatformSystem.currentTimeMillis()
        lock.withLock {
            val prev = currentSnapshot.browser
            val mappedElements = page?.elements?.take(60)?.map { el ->
                BrowserElementSummary(
                    index = el.index,
                    tagName = el.tagName,
                    type = el.type.orEmpty(),
                    name = el.name.orEmpty(),
                    elementId = el.elementId.orEmpty(),
                    text = el.text,
                    placeholder = el.placeholder.orEmpty(),
                    value = if (el.isSecret) "" else el.value.orEmpty(),
                    href = el.href.orEmpty(),
                    isClickable = el.isClickable,
                    isInputField = el.isInputField,
                    isSecret = el.isSecret,
                    formId = el.formId.orEmpty()
                )
            } ?: prev.elements

            val resolvedWaiting = waitingForUser ?: prev.waitingForUser
            val updated = prev.copy(
                isVisible = true,
                url = page?.url ?: prev.url,
                title = page?.title ?: prev.title,
                statusCode = page?.statusCode ?: prev.statusCode,
                lastAction = actionDescription.ifBlank { prev.lastAction },
                contentPreview = page?.contentText?.take(3000) ?: prev.contentPreview,
                rawHtml = page?.rawHtml?.take(120_000) ?: prev.rawHtml,
                elements = mappedElements,
                controlOwner = controlOwner ?: prev.controlOwner,
                waitingForUser = resolvedWaiting,
                takeoverReason = takeoverReason ?: prev.takeoverReason,
                takeoverMessage = takeoverMessage ?: prev.takeoverMessage,
                updatedAtMs = now
            )
            publishLocked(currentSnapshot.copy(browser = updated))
        }
    }

    fun setBrowserControl(
        owner: String,
        waitingForUser: Boolean = false,
        reason: String = "",
        message: String = "",
        makeVisible: Boolean = true
    ) {
        val now = PlatformSystem.currentTimeMillis()
        lock.withLock {
            val prev = currentSnapshot.browser
            val updated = prev.copy(
                isVisible = prev.isVisible || makeVisible,
                controlOwner = owner,
                waitingForUser = waitingForUser,
                takeoverReason = if (waitingForUser) reason else "",
                takeoverMessage = if (waitingForUser) message else "",
                lastAction = if (owner == "USER") "User took over browser control" else "Control returned to AI agent",
                updatedAtMs = now
            )
            publishLocked(currentSnapshot.copy(browser = updated))
        }
    }

    fun isBrowserUserControlled(): Boolean = lock.withLock {
        currentSnapshot.browser.controlOwner == "USER"
    }

    fun updateTerminal(
        executionId: String? = null,
        shellName: String? = null,
        workingDir: String? = null,
        script: String? = null,
        outputText: String? = null,
        exitCode: Int? = null,
        status: String? = null,
        controlOwner: String? = null,
        waitingForUser: Boolean? = null,
        isVisible: Boolean = true
    ) {
        val now = PlatformSystem.currentTimeMillis()
        lock.withLock {
            val prev = currentSnapshot.terminal
            val updated = prev.copy(
                isVisible = prev.isVisible || isVisible,
                executionId = executionId ?: prev.executionId,
                shellName = shellName ?: prev.shellName,
                workingDir = workingDir ?: prev.workingDir,
                currentScript = script ?: prev.currentScript,
                outputText = outputText?.takeLast(24_000) ?: prev.outputText,
                exitCode = exitCode ?: prev.exitCode,
                status = status ?: prev.status,
                controlOwner = controlOwner ?: prev.controlOwner,
                waitingForUser = waitingForUser ?: prev.waitingForUser,
                updatedAtMs = now
            )
            publishLocked(currentSnapshot.copy(terminal = updated))
        }
    }

    fun setTerminalControl(owner: String, waitingForUser: Boolean = false, makeVisible: Boolean = true) {
        val now = PlatformSystem.currentTimeMillis()
        lock.withLock {
            val prev = currentSnapshot.terminal
            val updated = prev.copy(
                isVisible = prev.isVisible || makeVisible,
                controlOwner = owner,
                waitingForUser = waitingForUser,
                updatedAtMs = now
            )
            publishLocked(currentSnapshot.copy(terminal = updated))
        }
    }

    fun isTerminalUserControlled(): Boolean = lock.withLock {
        currentSnapshot.terminal.controlOwner == "USER"
    }

    fun updateDownloadProgress(
        progress: DownloadProgress,
        availableStorageBytes: Long = -1L,
        isVisible: Boolean = true
    ) {
        val now = PlatformSystem.currentTimeMillis()
        val fileName = progress.destinationPath
            .replace('\\', '/')
            .substringAfterLast('/')
            .ifBlank { progress.downloadId }

        val entry = DownloadActivityEntry(
            downloadId = progress.downloadId,
            fileName = fileName,
            url = progress.activeUrl,
            destinationPath = progress.destinationPath,
            status = progress.status.name,
            percentage = progress.percentage.coerceIn(0, 100),
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            message = progress.message,
            updatedAtMs = now
        )

        lock.withLock {
            val prev = currentSnapshot.downloads
            val mutable = prev.items.toMutableList()
            val idx = mutable.indexOfFirst { it.downloadId == entry.downloadId }
            if (idx >= 0) {
                mutable[idx] = entry
            } else {
                mutable.add(0, entry)
            }
            val updated = prev.copy(
                isVisible = prev.isVisible || isVisible,
                availableStorageBytes = if (availableStorageBytes >= 0L) availableStorageBytes else prev.availableStorageBytes,
                items = mutable.take(20),
                updatedAtMs = now
            )
            publishLocked(currentSnapshot.copy(downloads = updated))
        }
    }

    fun setDownloadControl(
        owner: String,
        availableStorageBytes: Long = -1L,
        makeVisible: Boolean = true
    ) {
        val now = PlatformSystem.currentTimeMillis()
        lock.withLock {
            val prev = currentSnapshot.downloads
            val updated = prev.copy(
                isVisible = prev.isVisible || makeVisible,
                controlOwner = owner,
                availableStorageBytes = if (availableStorageBytes >= 0L) availableStorageBytes else prev.availableStorageBytes,
                updatedAtMs = now
            )
            publishLocked(currentSnapshot.copy(downloads = updated))
        }
    }

    fun isDownloadUserControlled(): Boolean = lock.withLock {
        currentSnapshot.downloads.controlOwner == "USER"
    }

    fun setPendingAuthorization(auth: PendingToolAuthorizationView?) {
        lock.withLock {
            publishLocked(currentSnapshot.copy(pendingAuthorization = auth))
        }
    }

    private fun publishLocked(next: ToolActivitySnapshot) {
        val waiting = next.pendingAuthorization != null ||
            next.browser.waitingForUser ||
            next.terminal.waitingForUser
        val resolved = next.copy(waitingForUserInput = waiting)
        currentSnapshot = resolved
        _snapshotFlow.value = resolved
    }
}
