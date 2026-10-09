plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    ohosArm64()
    sourceSets.androidMain.dependencies { api("com.tencent.kuikly-open:core-render-android:${libs.versions.kuikly.get()}") }
    sourceSets.commonMain.dependencies {
        api(project(":jverification-core"))
        api(libs.kuikly.core)
    }
}

publishing {
    repositories.maven {
        name = "staging"
        url = uri(rootProject.layout.buildDirectory.dir("maven"))
    }
}

android {
    namespace = "io.github.gycrosskit.jverification.kuikly"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
