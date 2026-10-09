plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.library) }
val componentVersion = providers.gradleProperty("jverificationVersion").orElse("0.1.5").get()
val verifyNativeModule = providers.gradleProperty("verifyNativeModule").orElse("false").get().toBoolean()
val kuiklyRenderFrameworkDir = providers.gradleProperty("kuiklyRenderFrameworkDir").orNull
kotlin {
    androidTarget(); ohosArm64()
    iosX64().binaries.framework {
        baseName = "JVerificationConsumer"
        if (verifyNativeModule) kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        export("com.github.gycrosskit.jverification:jverification-core:$componentVersion")
    }
    iosArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        if (verifyNativeModule) kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        export("com.github.gycrosskit.jverification:jverification-core:$componentVersion")
    }
    iosSimulatorArm64().binaries.framework {
        baseName = "JVerificationConsumer"
        if (verifyNativeModule) kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        export("com.github.gycrosskit.jverification:jverification-core:$componentVersion")
    }
    sourceSets {
        commonMain.dependencies { api("com.github.gycrosskit.jverification:jverification-core:$componentVersion") }
        if (verifyNativeModule) {
            commonMain { kotlin.srcDir("src/receiverCommon/kotlin") }
            commonMain.dependencies { implementation("com.github.gycrosskit.jverification:jverification-kuikly:$componentVersion") }
            androidMain { kotlin.srcDir("src/receiverAndroid/kotlin") }
        } else {
            ohosArm64Main.dependencies { implementation("com.github.gycrosskit.jverification:jverification-kuikly:$componentVersion") }
        }
    }
}
android {
    namespace = "io.github.gycrosskit.jverification.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
