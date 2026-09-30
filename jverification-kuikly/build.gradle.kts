plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    ohosArm64()
    sourceSets.commonMain.dependencies {
        api(project(":jverification-core"))
        implementation(libs.kuikly.core)
    }
}

publishing {
    repositories.maven {
        name = "staging"
        url = uri(rootProject.layout.buildDirectory.dir("maven"))
    }
}
