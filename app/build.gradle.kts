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
        versionCode = 61
        versionName = "0.1.15"

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
        }
        create("headunit") {
            dimension = "target"
            // Same applicationId as standard — this is a separate signer, not a separate app;
            // installing it requires uninstalling the standard build first (and vice versa).
            versionNameSuffix = "-headunit"
            buildConfigField("boolean", "HEADUNIT_SYSTEM", "true")
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

// Force the headunit flavor (debug and release) onto the platform-signed config so
// android:sharedUserId="android.uid.system" in src/headunit/AndroidManifest.xml actually
// resolves to uid 1000 on a matching device/emulator. AGP 8 exposes ApplicationVariant
// .signingConfig as a read-only val; setSigningConfig still exists on the implementation.
afterEvaluate {
    if (!hasHeadunitSigning) return@afterEvaluate
    val platform = android.signingConfigs.getByName("headunitPlatform")
    android.applicationVariants.configureEach {
        if (flavorName != "headunit") return@configureEach
        val setter = javaClass.methods.firstOrNull { method ->
            method.name == "setSigningConfig" && method.parameterCount == 1
        }
        if (setter != null) {
            setter.invoke(this, platform)
            logger.lifecycle("headunit signing: $name -> headunitPlatform")
        } else {
            logger.warn("headunit signing: setSigningConfig missing on $name — sign with apksigner")
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