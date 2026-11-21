package com.reveila.system

/**
 * System wide constant values.
 *
 * @author Charles Lee
 */
object Constants {
    const val SYSTEM_NAME = "system.name"
    const val SYSTEM_VERSION = "system.version"
    const val DEFAULT_SYSTEM_VERSION = "1.0.0"
    const val ENV_CALLBACK_URL = "REVEILA_CALLBACK_URL"
    const val ENV_JIT_TOKEN = "REVEILA_JIT_TOKEN"
    const val ENV_REVEILA_VERSION = "REVEILA_VERSION"
    const val SYSTEM_HOME = "system.home"
    const val RESET_HOME = "reset.home"
    const val PLATFORM = "platform"
    const val PLATFORM_OS = "platform.os"
    const val LOG_FILE_SIZE = "logging.file.size"
    const val LOG_FILE_COUNT = "logging.file.count"
    const val LOG_LEVEL = "logging.level"
    const val LOG_CHAT_ARCHIVE_ENABLED = "logging.chat.archive.enabled"
    const val LOG_CHAT_ARCHIVE_FILE_SIZE = "logging.chat.archive.file.size"
    const val LOG_CHAT_ARCHIVE_FILE_COUNT = "logging.chat.archive.file.count"
    const val SYSTEM_PROPERTIES = "reveila.properties"
    const val CHARACTER_ENCODING = "text.character.encoding"
    const val LAUNCH_STRICT_MODE = "launch.strict.mode"
    const val DB_CREATE_SCHEMA = "db.create.schema"
    const val SYSTEM_MODE = "system.mode"

    const val REMOTE_REVEILA = "remote.reveila"
    const val CLUSTER_SERVICE = "ClusterService"
    const val CLUSTER_NODE_ID = "cluster.node.id"
    const val CLUSTER_NODE_URL = "cluster.node.url"
    const val CLUSTER_ROLE = "cluster.role"
    const val CLUSTER_SEEDS = "cluster.seeds"
    const val CLUSTER_LEADER_URL = "cluster.leader.url"
    const val CLUSTER_HEARTBEAT_INTERVAL_MS = "cluster.heartbeat.interval.ms"
    const val CLUSTER_LEADER_TIMEOUT_MS = "cluster.leader.timeout.ms"
    const val CLUSTER_NODE_TIMEOUT_MS = "cluster.node.timeout.ms"
    const val CLUSTER_SECRET = "cluster.secret"
    const val CLUSTER_TOKEN_HEADER = "X-Reveila-Cluster-Token"
    const val CLUSTER_CHANNEL_CONFIG = "reveila_config_updates"
    const val CLUSTER_CHANNEL_VECTOR = "reveila_vector_updates"
    const val CLUSTER_CHANNEL_ROUTING = "reveila_routing_updates"
    const val CLUSTER_CHANNEL_PLUGIN = "reveila_plugin_updates"
    const val DESCRIPTION = "description"
    const val NAME = "name"
    const val VERSION = "version"
    const val CLASS = "class"
    const val METHODS = "methods"
    const val THREAD_SAFE = "thread-safe"
    const val VALUE = "value"
    const val ARGUMENTS = "arguments"
    const val RUNNABLE = "runnable"
    const val RUNNABLE_DELAY = "delay.seconds"
    const val RUNNABLE_INTERVAL = "interval.seconds"
    const val RUNNABLE_METHOD = "method.name"
    const val AUTHOR = "author"
    const val LICENSE_TOKEN = "license-token"
    const val COMPONENT = "component"
    const val TYPE = "type"
    const val CONFIGS_DIR_NAME = "configs"
    const val JOB_ARG_LASTRUN = "LastRun"
    const val JOB_ARG_DELAY = "Delay"
    const val JOB_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    const val TASK = "task"
    const val LIB_DIR_NAME = "libs"
    const val STANDALONE_MODE = "system.standalone"
    const val PLUGIN = "plugin"
    const val SYSTEM = "system"
    const val DIRECTORY = "directory"
    const val CAPABILITIES = "capabilities"
    const val HOT_DEPLOY = "hot-deploy"
    const val DEPENDENCIES = "dependencies"
    const val COMPONENT_START_TIMEOUT = "component.start.timeout"
    const val LOG_CONSOLE_ENABLED = "log.console.enabled"
    const val ISOLATION = "isolation"
    const val SECURITY_PERIMETER = "security-perimeter"
    const val NETWORK = "network"
    const val RESTRICTED = "restricted"
    const val AUTO_START = "auto-start"
    const val SERVICE = "service"
    const val DISPLAY_NAME = "displayName"
    const val PROVIDER = "provider"
    const val MANEFEST = "manifest"
    const val REQUIRED_ROLES = "required-roles"
    const val REQUIRED_PERMISSIONS = "required-permissions"
    const val REQUIRED_CAPABILITIES = "required-capabilities"
    const val REQUIRED_COMPONENTS = "required-components"
    const val REQUIRED_LIBRARIES = "required-libraries"
    const val REQUIRED_PLUGINS = "required-plugins"
    const val REQUIRED_PLUGINS_RESTART = "required-plugins-restart"
    const val REQUIRED_PLUGINS_RESTART_ON_STOP = "required-plugins-restart-on-stop"
    const val REQUIRED_PLUGINS_RESTART_ON_START = "required-plugins-restart-on-start"
    const val REQUIRED_PLUGINS_RESTART_ON_HOT_DEPLOY = "required-plugins-restart-on-hot-deploy"
    const val REQUIRED_PLUGINS_RESTART_ON_RELOAD = "required-plugins-restart-on-reload"
    const val REQUIRED_PLUGINS_RESTART_ON_RESTART = "required-plugins-restart-on-restart"
    const val REQUIRED_PLUGINS_RESTART_ON_UNLOAD = "required-plugins-restart-on-unload"
    const val REQUIRED_PLUGINS_RESTART_ON_UNINSTALL = "required-plugins-restart-on-uninstall"
    const val REQUIRED_PLUGINS_RESTART_ON_INSTALL = "required-plugins-restart-on-install"
    const val REQUIRED_PLUGINS_RESTART_ON_UPDATE = "required-plugins-restart-on-update"
    const val REQUIRED_PLUGINS_RESTART_ON_RESTART_ALL = "required-plugins-restart-on-restart-all"
    const val REQUIRED_PLUGINS_RESTART_ON_RELOAD_ALL = "required-plugins-restart-on-reload-all"
    const val REQUIRED_PLUGINS_RESTART_ON_UNLOAD_ALL = "required-plugins-restart-on-unload-all"
    const val REQUIRED_PLUGINS_RESTART_ON_UNINSTALL_ALL = "required-plugins-restart-on-uninstall-all"
    const val REQUIRED_PLUGINS_RESTART_ON_INSTALL_ALL = "required-plugins-restart-on-install-all"
    const val REQUIRED_PLUGINS_RESTART_ON_UPDATE_ALL = "required-plugins-restart-on-update-all"

    const val AI_STATUS_COMPLETED = "COMPLETED"
    const val AI_STATUS_INSUFFICIENT_CONTEXT = "INSUFFICIENT CONTEXT"
    const val AI_STATUS_ESCALATE = "ESCALATE"
    const val AI_STATUS_FAILED = "FAILED"
    const val AI_STATUS_TOOL_CALL = "TOOL CALL"

    // Human-readable display labels for the 6 predefined task routing categories
    const val INTENT_LABEL_LOCAL_TEXT_TRANSFORM = "Summarize & Rewrite"
    const val INTENT_LABEL_LOCAL_STRUCTURED_EXTRACT = "Extract & Organize Data"
    const val INTENT_LABEL_LOCAL_DEVICE_ACTION = "Quick Device & Doc Actions"
    const val INTENT_LABEL_CLOUD_LIVE_RETRIEVAL = "Live Web & Real-Time Info"
    const val INTENT_LABEL_CLOUD_DEEP_REASONING = "Deep Analysis & Coding"
    const val INTENT_LABEL_CLOUD_MULTI_STEP_AGENT = "Multi-Step Automation"

    val TASK_INTENT_LABELS: Map<String, String> = mapOf(
        "LOCAL_TEXT_TRANSFORM" to INTENT_LABEL_LOCAL_TEXT_TRANSFORM,
        "LOCAL_STRUCTURED_EXTRACT" to INTENT_LABEL_LOCAL_STRUCTURED_EXTRACT,
        "LOCAL_DEVICE_ACTION" to INTENT_LABEL_LOCAL_DEVICE_ACTION,
        "CLOUD_LIVE_RETRIEVAL" to INTENT_LABEL_CLOUD_LIVE_RETRIEVAL,
        "CLOUD_DEEP_REASONING" to INTENT_LABEL_CLOUD_DEEP_REASONING,
        "CLOUD_MULTI_STEP_AGENT" to INTENT_LABEL_CLOUD_MULTI_STEP_AGENT
    )

    fun formatTaskIntentLabel(category: String): String {
        val key = category.trim().uppercase()
        return TASK_INTENT_LABELS[key] ?: category
    }
}
