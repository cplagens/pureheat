import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Upload key for Play App Signing, never in the repo: locally from keystore.properties (in
// .gitignore), in CI from environment variables (GitHub secrets, see .github/workflows/release.yml).
// Without either the release build stays unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun signingValue(property: String, env: String): String? = System.getenv(env) ?: keystoreProperties.getProperty(property)

// Version: `appVersion` in gradle.properties; a release build in CI passes the tag (v1.2.3 →
// -PappVersion=1.2.3). versionCode = major·10000 + minor·100 + patch, so it grows with every
// release (minor and patch below 100).
val appVersion = providers.gradleProperty("appVersion").get()
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "minor and patch must be below 100: $appVersion" }
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "de.cplagens.pureheat"
    // Google Play requires targetSdk 36 (Android 16) for new apps and updates since 2026-08-31.
    compileSdk = 36

    defaultConfig {
        applicationId = "de.cplagens.pureheat"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "PUREHEAT_KEYSTORE")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "PUREHEAT_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PUREHEAT_KEY_ALIAS")
                // PKCS12 keystores (keytool's default) use the store password for the key.
                keyPassword = signingValue("keyPassword", "PUREHEAT_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // On-device Gemini Nano for turning room descriptions into properties (falls back to keywords).
    implementation(libs.mlkit.genai.prompt)
    // Voluntary "buy me a coffee" tip (data/support/CoffeeBilling).
    implementation(libs.play.billing)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
