# ==============================================================================
# REVEILA SUITE - LIBRARY RULES (Internal Logic & Dependencies)
# ==============================================================================

# 1. PUBLIC API PRESERVATION
# Ensures your public methods remain accessible to external developers.
-keep public class com.reveila.api.** {
    public protected *;
}

# 2. INTERNAL REFLECTION TARGETS
# Redundant safety for internal classes accessed via dynamic invocation.
-keep class com.reveila.** {
    all <fields>;
    all <methods>;
    public <init>(...);
}

# 3. THIRD-PARTY SDK SAFETY (AWS & JACKSON)
# Prevents the build from failing on missing optional dependencies.
-keep class software.amazon.awssdk.** { *; }
-dontwarn software.amazon.awssdk.**
-dontwarn com.fasterxml.jackson.**

# 4. OPTIMIZATION SETTINGS
# Prevents R8 from merging interfaces, which can break dynamic plugin loading.
-nointerfacecompression
-dontmergeinterfaces
-printusage
