plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.library) }
val componentVersion = providers.gradleProperty("jverificationVersion").orElse("0.1.3").get()
kotlin {
    androidTarget(); jvm(); iosX64(); ohosArm64()
    iosArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        export("com.github.gycrosskit.jverification:jverification-core:$componentVersion")
    }
    iosSimulatorArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        export("com.github.gycrosskit.jverification:jverification-core:$componentVersion")
    }
    sourceSets {
        commonMain.dependencies { api("com.github.gycrosskit.jverification:jverification-core:$componentVersion") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.jverification:jverification-kuikly:$componentVersion") }
    }
}
android {
    namespace = "io.github.gycrosskit.jverification.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
