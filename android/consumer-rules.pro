# ==============================================================================
# REVEILA SUITE - CONSUMER RULES (Public API & Connectivity)
# ==============================================================================

# 1. CORE INTERFACES & PLATFORM ADAPTERS
# Ensures the host app doesn't rename the "hooks" your library uses.
-keep interface com.reveila.system.** { *; }
-keep class com.reveila.event.** { *; }
-keep class com.reveila.android.ReveilaPlatformAdapter { *; }

# 2. THE AGENTIC FABRIC (Reflection & Internal Logic)
# Protects private fields/methods so ReflectionMethod.invoke() works.
-keep class com.reveila.** {
    all <fields>;
    all <methods>;
    public <init>(...);
}

# 3. DATA MODELS & DTOs
# Prevents "Class has no fields" errors during JSON/Vault serialization.
-keepclassmembers class com.reveila.model.** {
    all <fields>;
    all <methods>;
}

# 4. ENUMS & ANNOTATIONS
# Vital for String-based Enum lookups and Component Discovery.
-keepclassmembers enum com.reveila.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 5. DEBUGGING & RUNTIME METADATA
# Helps you read stack traces in VS Code and allows AI to see parameter names.
-keepattributes SourceFile, LineNumberTable
-keepparameternames

# 6. KOTLIN COMPATIBILITY
-keep class kotlin.Metadata { *; }