package reveila.system;

/**
 * @author Charles Lee
 *
 * This class defines system wide constant values.
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
	public static final String LOGGER_NAME = "logger.name";
	public static final String SYSTEM_PROPERTIES_FILE_NAME = "reveila.properties";
	public static final String CRYPTOGRAPHER_SECRETKEY = "cryptographer.secretkey";
	public static final String CHARACTER_ENCODING = "text.character.encoding";
	public static final String LAUNCH_STRICT_MODE = "launch.strict.mode";
	public static final String SYSTEM_DATA_FILE_DIR = "system.data.file.dir";
	public static final String SYSTEM_TEMP_FILE_DIR = "system.temp.file.dir";
	public static final String TASK_INITIAL_DELAY = "task.initial.delay";
	public static final String TASK_INTERVAL = "task.interval";
	public static final String INVOCATION_TIME_TRACKING_MAX_ENTRIES = "invocation.time.tracking.max.entries";
	
	/*
	 * **************************************************
	 * Used in configuration JSON files
	 * **************************************************
	 */
	public static final String REMOTE_REVEILA = "remote.reveila";
	public static final String DESCRIPTION = "description";
	public static final String START = "start";
	public static final Object ENABLE = "enable";
    public static final String NAME = "name";
	public static final String VERSION = "version";
	public static final String CLASS = "class";
	public static final String THREAD_SAFE = "thread-safe";
	public static final String VALUE = "value";
	public static final String ARGUMENTS = "arguments";
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
	public static final String STANDALONE_MODE = "standalone.mode";
}
