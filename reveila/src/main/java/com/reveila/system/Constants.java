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
	public static final String PLATFORM_OS = "platform.os";
	public static final String LOG_FILE_SIZE = "logging.file.size";
	public static final String LOG_FILE_COUNT = "logging.file.count";
	public static final String LOG_LEVEL = "logging.level";
	public static final String SYSTEM_PROPERTIES_FILE_NAME = "reveila.properties";
	public static final String CRYPTOGRAPHER_SECRETKEY = "cryptographer.secretkey";
	public static final String CHARACTER_ENCODING = "text.character.encoding";
	public static final String LAUNCH_STRICT_MODE = "launch.strict.mode";

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
	public static final String DIRECTORY = "directory";
	public static final String CAPABILITIES = "capabilities";
	public static final String HOT_DEPLOY = "hot-deploy";
	public static final String START_PRIORITY = "start-priority";
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
}
