package reveila.system;

/**
 * @author Charles Lee
 *
 * This class defines system wide constant values.
 */
public final class Constants {

	/*
	 * **************************************************
	 * used in system.properties file
	 * **************************************************
	 */
	public static final String S_SERVER_NAME = "server.name";
	public static final String S_SYSTEM_MODE = "system.mode";
    public static final String S_SYSTEM_HOME = "system.home";
	public static final String S_SYSTEM_DEBUG = "system.debug";
	public static final String S_SYSTEM_LOGGING_FILE = "system.logging.file";
	public static final String S_SYSTEM_LOGGING_LEVEL = "system.logging.level";
	public static final String S_SYSTEM_ACCOUNT = "system.account";
	public static final String S_SYSTEM_PASSWORD = "system.password";
	public static final String S_SYSTEM_FILE_STORE = "system.data.file.store";
	public static final String S_SYSTEM_TMP_FILE_STORE = "system.data.file.temp";
	public static final String S_SERVER_DISPLAY_NAME = "server.display.name";
	public static final String S_SERVICE_LOADER_TIMEOUT = "service.loader.timeout";
	public static final String S_SERVICE_LOADER_COUNT = "service.loader.count";
	public static final String S_SYSTEM_PROPERTIES_URL = "system.properties.url";
	public static final String S_SYSTEM_PROPERTIES_FILE_NAME = "system.properties";
	public static final String S_SYSTEM_CRYPTOGRAPHER_SECRETKEY = "system.cryptographer.secretkey";
	public static final String S_SYSTEM_CONFIGURATION_FORMAT= "system.configuration.format";
	public static final String S_SYSTEM_JOB_CONFIG_DIR = "system.job.config.dir";
	
	/*
	 * **************************************************
	 * used in configuration files
	 * **************************************************
	 */
	public static final String C_ROOT = "configuration";
	public static final String C_SERVICE = "service";
	public static final String C_DESCRIPTION = "description";
	public static final String C_START = "start";
	public static final String C_DOMAIN = "domain";
	public static final String C_NAME = "name";
	public static final String C_DISPLAY_NAME = "display-name";
	public static final String C_VERSION = "version";
	public static final String C_CLASS = "class";
	public static final String C_PROVIDER = "provider";
	public static final String C_DEPEND = "depend";
	public static final String C_PROPERTY = "property";
	public static final String C_PROPERTY_TYPE = "type";
	public static final String C_PROPERTY_VALUE = "value";
	public static final String C_ENCRYPT = "encrypt";
	public static final String C_SCOPE = "scope";
	public static final String C_THREAD_SAFE = "thread-safe";
	public static final String C_RUN_AS = "run-as";
	public static final String C_USERNAME = "username";
	public static final String C_PASSWORD = "password";
	public static final String C_EVENT = "event";
	public static final String C_LISTEN = "listen";
	public static final String C_EMIT = "emit";
	public static final String C_EVENT_CLASS = "event-class";
	public static final String C_EMAIL_ADDRESS = "email-address";
	public static final String C_LIST = "list";
	public static final String C_ARRAY = "array";
	public static final String C_VALUE = "value";
	public static final String C_APP_DATA_DIR_NAME = "Application Data";
	public static final String C_TMP_DATA_DIR_NAME = "Temp Files";
	public static final String C_ARGUMENTS = "arguments";
	public static final String C_AUTHOR = "author";
	public static final String C_COPYRIGHT = "copyright";
	public static final String C_LICENSE_TYPE = "license-type";
	public static final String C_LICENSE_TOKEN = "license-token";
	public static final String C_CONFIGURATION_FILE_URL = "configuration-file-url";
	public static final String C_COMPONENTS = "components";
	public static final String C_OBJECT = "object";
	public static final String C_ID = "id";
	public static final String C_ARGUMENT = "argument";
	public static final String C_TYPE = "type";
	public static final String C_DEPENDS = "depends";
	public static final String C_PREREQUISITES = "prerequisites";
	public static final String C_PREREQUISITE = "prerequisite";
	public static final String C_PROPERTIES = "properties";
	public static final String C_LIB_DIR_NAME = "libs";
    public static final String LOG_DIR_NAME = "logs";
    
	
	public final class DataStoreConstants {
		
		public static final String ENTITY = "entity";
		public static final String ATTRIBUTE = "attribute";
		public static final String REFERENCE = "reference";
		public static final String REFERENCED_BY = "referenced-by";
		public static final String STORED_NAME = "stored-name";
		public static final String CHARSET = "charset";
		public static final String AUTO_VALUE = "auto-value";
		public static final String MUTABLE = "mutable";
		public static final String KEY = "key";
		public static final String TYPE = "type";
		public static final String LENGTH = "length";
		public static final String PRECISION = "precision";
		public static final String MULTI_VALUE = "multi-value";
		public static final String PATTERN = "pattern";
		public static final String ENCRYPTED = "encrypted";
		public static final String VALID_VALUES = "valid-values";
		public static final String ALLOW_NULL = "allow-null";
		public static final String DEFAULT_VALUE = "default-value";
	}

}
