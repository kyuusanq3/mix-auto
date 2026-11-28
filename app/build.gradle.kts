import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)
}

// Headunit flavor signing: AOSP platform test key, converted to a keystore outside the repo
// (never check in platform.pk8 / platform.x509.pem or the converted keystore). See
// docs/headunit-s10a-qcm6125.md and .cursor/rules for why this is required for the
// android:sharedUserId="android.uid.system" embed path. Absent file = build falls back to
// the standard release signing config (harmless; just won't run as uid 1000 on device).
val headunitSigningPropsFile = rootProject.file("headunit-signing.properties")
val headunitSigningProps = Properties().apply {
    if (headunitSigningPropsFile.exists()) {
        FileInputStream(headunitSigningPropsFile).use { load(it) }
    }
}
val hasHeadunitSigning = headunitSigningPropsFile.exists()

android {
    namespace = "com.kyuusanq3.mixauto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kyuusanq3.mixauto"
        minSdk = 24
        targetSdk = 34
        versionCode = 66
        versionName = "0.1.20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("mixauto-release.jks")
            storePassword = "mixauto123"
            keyAlias = "mixauto"
            keyPassword = "mixauto123"
        }
        if (hasHeadunitSigning) {
            create("headunitPlatform") {
                storeFile = file(headunitSigningProps.getProperty("storeFile"))
                storePassword = headunitSigningProps.getProperty("storePassword")
                keyAlias = headunitSigningProps.getProperty("keyAlias")
                keyPassword = headunitSigningProps.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "target"
    productFlavors {
        create("standard") {
            dimension = "target"
            buildConfigField("boolean", "HEADUNIT_SYSTEM", "false")
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"\"")
            buildConfigField("String", "UPDATE_ASSET_NAME", "\"mix-auto.apk\"")
        }
        create("headunit") {
            dimension = "target"
            // Own package so this APK installs beside standard Mix Auto. Display name and
            // launcher icon come from src/headunit/res (Max Auto, amber pin).
            applicationId = "com.kyuusanq3.maxauto"
            versionNameSuffix = "-headunit"
            buildConfigField("boolean", "HEADUNIT_SYSTEM", "true")
            // Own GitHub release line. releases/latest stays Mix Auto; Max Auto lists
            // tags max-auto-v* and downloads max-auto.apk from the newest match.
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"max-auto-v\"")
            buildConfigField("String", "UPDATE_ASSET_NAME", "\"max-auto.apk\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

// Headunit APKs must be signed with the platform key so android:sharedUserId="android.uid.system"
// resolves to uid 1000. AGP 8.6 does not expose a settable variant signingConfig (the reflective
// setSigningConfig hook never matches), so re-sign the assembled headunit APK in place with
// apksigner from the newest build-tools. Only runs when headunit-signing.properties exists.
afterEvaluate {
    if (!hasHeadunitSigning) return@afterEvaluate
    tasks.matching { it.name == "assembleHeadunitDebug" || it.name == "assembleHeadunitRelease" }.configureEach {
        doLast {
            val buildToolsDir = File(android.sdkDirectory, "build-tools").listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.name }
                ?: throw GradleException("headunit signing: no build-tools under ${android.sdkDirectory}")
            val isWindows = System.getProperty("os.name").startsWith("Windows")
            val apksigner = File(buildToolsDir, if (isWindows) "apksigner.bat" else "apksigner")
            val buildType = name.removePrefix("assembleHeadunit").lowercase()
            val apkDir = layout.buildDirectory.dir("outputs/apk/headunit/$buildType").get().asFile
            val apks = apkDir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
            if (apks.isEmpty()) throw GradleException("headunit signing: no APK in $apkDir")
            val storeFile = headunitSigningProps.getProperty("storeFile")
            val storePass = headunitSigningProps.getProperty("storePassword")
            val keyAlias = headunitSigningProps.getProperty("keyAlias")
            val keyPass = headunitSigningProps.getProperty("keyPassword")
            apks.forEach { apk ->
                val proc = ProcessBuilder(
                    apksigner.absolutePath, "sign",
                    "--ks", storeFile,
                    "--ks-key-alias", keyAlias,
                    "--ks-pass", "pass:$storePass",
                    "--key-pass", "pass:$keyPass",
                    apk.absolutePath,
                ).redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().readText()
                if (proc.waitFor() != 0) throw GradleException("headunit signing failed for ${apk.name}:\n$output")
                logger.lifecycle("headunit signing: ${apk.name} -> platform key")
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(files("src/main/java"))
}

tasks.named("check") {
    dependsOn("detekt")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}