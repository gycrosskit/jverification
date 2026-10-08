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
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    ohosArm64()
    sourceSets {
        commonMain.dependencies { api(libs.coroutines.core) }
        androidMain.dependencies {
            api("cn.jiguang.sdk:jverification:3.4.8")
            implementation("cn.jiguang.sdk:jcore:5.5.6")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2-1.0.0")
        }
        // 用测试 transport 执行生产 Kuikly 模块，覆盖关闭和迟到回调。
        jvmTest {
            kotlin.srcDir(rootProject.file("jverification-kuikly/src/commonMain/kotlin"))
            // 以受控 Android/SDK 边界执行实际 driver，不复制其 owner/回调状态机。
            kotlin.srcDir("src/androidMain/kotlin")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2-1.0.0")
        }
    }
}

android {
    namespace = "io.github.gycrosskit.jverification"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    repositories.maven {
        name = "staging"
        url = uri(rootProject.layout.buildDirectory.dir("maven"))
    }
}
