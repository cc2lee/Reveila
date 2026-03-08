In the Reveila Android application, system.home is passed and initialized in several key locations:

1. Default Internal Path Resolution
In AndroidPlatformAdapter.java, a static helper method defines the default location within the app's internal storage:

public static String getSystemHome(Context context) {
    return new File(context.getFilesDir(), "reveila/system").getAbsolutePath();
}

2. Service Initialization (Intent Handling)
In ReveilaService.java, the service extracts a custom path from the starting Intent if provided:

final String customSystemHome = intent != null ? intent.getStringExtra("systemHome") : null;
// ...
if (customSystemHome != null) {
    homePath = customSystemHome;
} else {
    homePath = AndroidPlatformAdapter.getSystemHome(this);
}

3. Property Injection
Later in ReveilaService.java, if a custom home was provided, it is explicitly set in the properties passed to the adapter:

Properties props = new Properties();
if (customSystemHome != null) {
    props.setProperty(com.reveila.system.Constants.SYSTEM_HOME, customSystemHome);
}
PlatformAdapter platformAdapter = new AndroidPlatformAdapter(this, props);

4. Fallback in the Adapter
Finally, in AndroidPlatformAdapter.java, the adapter ensures the property is set during property loading if it wasn't already provided:

if (!properties.containsKey(Constants.SYSTEM_HOME)) {
    properties.setProperty(Constants.SYSTEM_HOME, getSystemHome(context));
}

5. Expo/React Native Bridge
For the UI layer, the ReveilaModule.kt exposes this to JavaScript, allowing the app to pass a path from TypeScript:

AsyncFunction("startService") { systemHome: String? ->
    val intent = Intent(context, ReveilaService::class.java).apply {
        putExtra("systemHome", systemHome)
    }
    // ... starts service with the extra
}
