plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = providers.environmentVariable("GROUP").orElse("io.github.gycrosskit").get()
    version = providers.environmentVariable("VERSION").orElse("0.1.0").get()
}
