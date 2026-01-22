# Rules for building the library

# 1. Preserve the Public API
# This is the most important rule. It tells R8: "Do not rename any public or 
# protected classes/methods in my public API packages."
-keep public class com.reveila.api.** {
    public protected *;
}

# 2. Scramble Internal Logic
# By NOT adding keep rules for your 'internal' or 'impl' packages, R8 will 
# automatically obfuscate them (e.g., InternalScanner -> a.b.c).
# No action needed here; the default behavior is to obfuscate what isn't kept.

# 3. Retain Metadata for Debugging
# Essential so that when your library crashes in a consumer's app, 
# the stack trace shows line numbers (even if names are obfuscated).
-keepattributes SourceFile, LineNumberTable, Signature, EnclosingMethod

# 4. AWS SDK Keep Rules
-keep class software.amazon.awssdk.** { *; }
-dontwarn software.amazon.awssdk.**