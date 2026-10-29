// Plugin versions live in gradle/libs.versions.toml.
// AGP 9 compiles Kotlin itself, so org.jetbrains.kotlin.android is intentionally not applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
