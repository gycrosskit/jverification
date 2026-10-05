plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.library) }
kotlin {
    androidTarget(); jvm(); iosX64(); ohosArm64()
    iosArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        export("com.github.gycrosskit.jverification:jverification-core:0.1.3")
    }
    iosSimulatorArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        export("com.github.gycrosskit.jverification:jverification-core:0.1.3")
    }
    sourceSets {
        commonMain.dependencies { api("com.github.gycrosskit.jverification:jverification-core:0.1.3") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.jverification:jverification-kuikly:0.1.3") }
    }
}
android {
    namespace = "io.github.gycrosskit.jverification.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
