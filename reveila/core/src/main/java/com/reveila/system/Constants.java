package com.reveila.system;

/**
 * @author Charles Lee
 *
 *         This class defines system wide constant values.
 */
public final class Constants {

	/*
	 * **************************************************
	 * Used in reveila.properties
	 * **************************************************
	 */
	public static final String SYSTEM_NAME = "system.name";
	public static final String SYSTEM_VERSION = "system.version";
	public static final String SYSTEM_HOME = "system.home";
	public static final String RESET_HOME = "reset.home";
	public static final String PLATFORM = "platform";
	public static final String PLATFORM_OS = "platform.os";
	public static final String LOG_FILE_SIZE = "logging.file.size";
	public static final String LOG_FILE_COUNT = "logging.file.count";
	public static final String LOG_LEVEL = "logging.level";
	public static final String SYSTEM_PROPERTIES = "reveila.properties";
	public static final String CHARACTER_ENCODING = "text.character.encoding";
	public static final String LAUNCH_STRICT_MODE = "launch.strict.mode";
	public static final String DB_CREATE_SCHEMA = "db.create.schema";

	/**
	 * System mode can be 'development', 'production' or 'demo'.
	 * In development mode, additional logging and debugging information may be available.
	 * In production mode, the system is optimized for performance and security.
	 * In demo mode, the system may use mock data and limited functionality for demonstration purposes.
	 */
	public static final String SYSTEM_MODE = "system.mode";

	/*
	 * **************************************************
	 * Used in configuration JSON files
	 * **************************************************
	 */
	public static final String REMOTE_REVEILA = "remote.reveila";
	public static final String DESCRIPTION = "description";
	public static final String NAME = "name";
	public static final String VERSION = "version";
	public static final String CLASS = "class";
	public static final String METHODS = "methods";
	public static final String THREAD_SAFE = "thread-safe";
	public static final String VALUE = "value";
	public static final String ARGUMENTS = "arguments";
	public static final String RUNNABLE = "runnable";
	public static final String RUNNABLE_DELAY = "delay.seconds";
	public static final String RUNNABLE_INTERVAL = "interval.seconds";
	public static final String RUNNABLE_METHOD = "method.name";
	public static final String AUTHOR = "author";
	public static final String LICENSE_TOKEN = "license-token";
	public static final String COMPONENT = "component";
	public static final String TYPE = "type";
	public static final String CONFIGS_DIR_NAME = "configs";
	public static final String JOB_ARG_LASTRUN = "LastRun";
	public static final String JOB_ARG_DELAY = "Delay";
	public static final String JOB_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	public static final String TASK = "task";
	public static final String LIB_DIR_NAME = "libs";
	public static final String STANDALONE_MODE = "system.standalone";
	public static final String PLUGIN = "plugin";
	public static final String SYSTEM = "system";
	public static final String DIRECTORY = "directory";
	public static final String CAPABILITIES = "capabilities";
	public static final String HOT_DEPLOY = "hot-deploy";
	public static final String DEPENDENCIES = "dependencies";
	public static final String COMPONENT_START_TIMEOUT = "component.start.timeout";
	public static final String LOG_CONSOLE_ENABLED = "log.console.enabled";
	public static final String ISOLATION = "isolation";
	public static final String SECURITY_PERIMETER = "security-perimeter";
	public static final String NETWORK = "network";
	public static final String RESTRICTED = "restricted";
	public static final String AUTO_START = "auto-start";
	public static final String SERVICE = "service";
	public static final String DISPLAY_NAME = "displayName";
	public static final String PROVIDER = "provider";
    public static final String MANEFEST = "manifest";
	public static final String REQUIRED_ROLES = "required-roles";
	public static final String REQUIRED_PERMISSIONS = "required-permissions";
	public static final String REQUIRED_CAPABILITIES = "required-capabilities";
	public static final String REQUIRED_COMPONENTS = "required-components";
	public static final String REQUIRED_LIBRARIES = "required-libraries";
    public static final String REQUIRED_PLUGINS = "required-plugins";
	public static final String REQUIRED_PLUGINS_RESTART = "required-plugins-restart";
	public static final String REQUIRED_PLUGINS_RESTART_ON_STOP = "required-plugins-restart-on-stop";
	public static final String REQUIRED_PLUGINS_RESTART_ON_START = "required-plugins-restart-on-start";
	public static final String REQUIRED_PLUGINS_RESTART_ON_HOT_DEPLOY = "required-plugins-restart-on-hot-deploy";
	public static final String REQUIRED_PLUGINS_RESTART_ON_RELOAD = "required-plugins-restart-on-reload";
	public static final String REQUIRED_PLUGINS_RESTART_ON_RESTART = "required-plugins-restart-on-restart";
	public static final String REQUIRED_PLUGINS_RESTART_ON_UNLOAD = "required-plugins-restart-on-unload";
	public static final String REQUIRED_PLUGINS_RESTART_ON_UNINSTALL = "required-plugins-restart-on-uninstall";
	public static final String REQUIRED_PLUGINS_RESTART_ON_INSTALL = "required-plugins-restart-on-install";
	public static final String REQUIRED_PLUGINS_RESTART_ON_UPDATE = "required-plugins-restart-on-update";
	public static final String REQUIRED_PLUGINS_RESTART_ON_RESTART_ALL = "required-plugins-restart-on-restart-all";
	public static final String REQUIRED_PLUGINS_RESTART_ON_RELOAD_ALL = "required-plugins-restart-on-reload-all";
	public static final String REQUIRED_PLUGINS_RESTART_ON_UNLOAD_ALL = "required-plugins-restart-on-unload-all";
	public static final String REQUIRED_PLUGINS_RESTART_ON_UNINSTALL_ALL = "required-plugins-restart-on-uninstall-all";
	public static final String REQUIRED_PLUGINS_RESTART_ON_INSTALL_ALL = "required-plugins-restart-on-install-all";
	public static final String REQUIRED_PLUGINS_RESTART_ON_UPDATE_ALL = "required-plugins-restart-on-update-all";

	public static final String AI_STATUS_COMPLETED = "[STATUS: COMPLETED]";
	public static final String AI_STATUS_INSUFFICIENT_CONTEXT = "[STATUS: INSUFFICIENT_CONTEXT]";
	public static final String AI_STATUS_ESCALATE = "[STATUS: ESCALATE]";
	public static final String AI_STATUS_FAILED = "[STATUS: FAILED]";

}
