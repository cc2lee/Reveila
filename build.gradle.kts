plugins {
    // This looks up 'android-library' in the [plugins] section of the TOML
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}