plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = providers.environmentVariable("GROUP").orElse("com.github.gycrosskit.jverification").get()
    version = providers.environmentVariable("VERSION").orElse("0.1.1").get()
}
