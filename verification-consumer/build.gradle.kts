plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.library) }
kotlin {
    androidTarget(); jvm(); iosArm64(); iosX64(); ohosArm64()
    iosSimulatorArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        export("com.github.gycrosskit.jverification:jverification-core:0.1.1")
    }
    sourceSets {
        commonMain.dependencies { api("com.github.gycrosskit.jverification:jverification-core:0.1.1") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.jverification:jverification-kuikly:0.1.1") }
    }
}
android {
    namespace = "io.github.gycrosskit.jverification.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
