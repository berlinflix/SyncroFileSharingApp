import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.zxing.core)
    implementation(libs.jna.platform)
}

val appVersion = "2.0.0"

compose.desktop {
    application {
        mainClass = "com.syncro.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dsun.java2d.uiScale.enabled=true")

        // jpackage is needed for installers. Point this at any full JDK 17+ if the one running Gradle lacks it:
        //   ./gradlew :desktop:packageMsi -Psyncro.packagingJdk="C:/Program Files/Microsoft/jdk-21"
        (providers.gradleProperty("syncro.packagingJdk").orNull ?: System.getenv("SYNCRO_PACKAGING_JDK"))?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Syncro"
            packageVersion = appVersion
            // Shown by Windows in Task Manager and the firewall prompt.
            description = "Syncro"
            vendor = "Syncro"
            copyright = "Syncro"
            modules("java.naming", "jdk.crypto.ec", "java.management", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/syncro.ico"))
                menuGroup = "Syncro"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8f5b7c1e-3d2a-4b8e-9a61-5c0f2e7d4a19"
            }
            macOS {
                bundleID = "com.syncro.desktop"
            }
            linux {
                iconFile.set(project.file("icons/syncro.png"))
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Branded single-file installer: ./gradlew :desktop:packageSetup -Psyncro.packagingJdk=<JDK with jpackage>
tasks.register<Exec>("packageSetup") {
    group = "compose desktop"
    description = "Builds desktop/build/setup/SyncroSetup-<version>.exe, the Syncro-styled per-user installer."
    dependsOn("createReleaseDistributable")
    val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/Syncro").get().asFile
    val script = rootProject.file("installer/build.ps1")
    inputs.dir(rootProject.file("installer"))
    inputs.dir(appImage)
    outputs.dir(layout.buildDirectory.dir("setup"))
    commandLine(
        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath,
        "-AppImage", appImage.absolutePath, "-Version", appVersion,
    )
}
