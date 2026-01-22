# Rules for the Apps that will import the library

# 1. Protect Reveila Core Interfaces
# We must keep the names of our PlatformAdapter and components so reflection works.
-keep interface com.reveila.system.** { *; }
-keep class com.reveila.event.** { *; }
-keep class com.reveila.android.ReveilaPlatformAdapter { *; }

# 2. Protect DTOs and Serialized Classes
# If you use Jackson/Gson to parse JSON into these objects, their field names must remain intact.
-keepclassmembers class com.reveila.model.** {
    <fields>;
    <methods>;
}

# 3. Preserve Annotations
# Essential if you use custom annotations for component discovery or dependency injection.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 4. Handle Kotlin Metadata
# Required if your library or the consuming app relies on Kotlin reflection.
-keep class kotlin.Metadata { *; }

# 5. Third-Party "Don't Warn"
# Stops the consumer's build from failing if they don't have certain 
# optional AWS or Jackson dependencies in their own classpath.
-dontwarn com.fasterxml.jackson.**
-dontwarn software.amazon.awssdk.**