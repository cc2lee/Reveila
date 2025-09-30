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
	public static final String S_SERVER_NAME = "system.name";
	public static final String S_SYSTEM_VERSION = "system.version";
	public static final String S_SYSTEM_MODE = "system.mode";
    public static final String S_SYSTEM_HOME = "system.home";
	public static final String S_SYSTEM_OS = "system.os";
	public static final String S_SYSTEM_LOGGING_FILE = "system.logging.file";
	public static final String S_SYSTEM_LOGGING_FILE_LIMIT = "system.logging.file.limit";
	public static final String S_SYSTEM_LOGGING_FILE_COUNT = "system.logging.file.count";
	public static final String S_SYSTEM_LOGGING_LEVEL = "system.logging.level";
	public static final String S_SERVER_DISPLAY_NAME = "system.display.name";
	public static final String S_SYSTEM_PROPERTIES_URL = "reveila.properties.url";
	public static final String S_SYSTEM_PROPERTIES_FILE_NAME = "reveila.properties";
	public static final String S_SYSTEM_CRYPTOGRAPHER_SECRETKEY = "system.cryptographer.secretkey";
	public static final String S_SYSTEM_CHARSET = "system.charset";
	public static final String S_SYSTEM_STRICT_MODE = "system.strict.mode";
	public static final String S_SYSTEM_LOGGER_NAME = "system.logger.name";
	public static final String S_SYSTEM_DATA_DIR = "system.data.file.store";
	public static final String S_SYSTEM_TMP_DATA_DIR = "system.data.file.temp";
	public static final String S_SYSTEM_REMOTE_SERVICE = "system.remote.service";

	public static final long PENALTY_UNIT = 5000;
	public static final long DELAY_TIME_UNIT = 5000; // 5 seconds
	
	/*
	 * **************************************************
	 * Used in configuration JSON files
	 * **************************************************
	 */
	public static final String C_DESCRIPTION = "description";
	public static final String C_START = "start";
	public static final Object C_ENABLE = "enable";
    public static final String C_NAME = "name";
	public static final String C_VERSION = "version";
	public static final String C_CLASS = "class";
	public static final String C_PROVIDER = "provider";
	public static final String C_THREAD_SAFE = "thread-safe";
	public static final String C_VALUE = "value";
	public static final String C_ARGUMENTS = "arguments";
	public static final String C_AUTHOR = "author";
	public static final String C_LICENSE_TOKEN = "license-token";
	public static final String C_COMPONENT = "component";
	public static final String C_TYPE = "type";
    public static final String C_CONFIGS_DIR_NAME = "configs";
    public static final String C_JOB_DIR_NAME = "jobs";
    public static final String C_JOB_ARG_LASTRUN = "LastRun";
    public static final String C_JOB_ARG_DELAY = "Delay";
    public static final String C_JOB_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	public static final String C_TASK = "task";
    public static final String C_LIB_DIR_NAME = "libs";
    
}
