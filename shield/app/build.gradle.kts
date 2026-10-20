import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release configuration comes from keystore.properties (local) or environment variables (CI).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(name: String, env: String, default: String): String =
    (project.findProperty(name) as String?) ?: System.getenv(env) ?: keystoreProps.getProperty(name) ?: default

val serverUrl = cfg("shield.serverUrl", "SHIELD_SERVER_URL", "https://api.family-shield.invalid")
val rustoreConsoleAppId = cfg("shield.rustoreConsoleAppId", "RUSTORE_CONSOLE_APP_ID", "0")
val rustorePushProjectId = cfg("shield.rustorePushProjectId", "RUSTORE_PUSH_PROJECT_ID", "")
val entitlementPublicKey = cfg("shield.entitlementPublicKey", "SHIELD_ENTITLEMENT_PUBLIC_KEY", "")

android {
    namespace = "ru.ubt.familyshield"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.ubt.familyshield"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "ENTITLEMENT_PUBLIC_KEY", "\"$entitlementPublicKey\"")
        buildConfigField("String", "RUSTORE_PUSH_PROJECT_ID", "\"$rustorePushProjectId\"")
        resValue("string", "rustore_console_app_id", rustoreConsoleAppId)
        resValue("string", "rustore_push_project_id", rustorePushProjectId)
        manifestPlaceholders["payScheme"] = "familyshieldpay"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val storePath = cfg("shield.storeFile", "SHIELD_STORE_FILE", "")
            if (storePath.isNotEmpty() && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = cfg("shield.storePassword", "SHIELD_STORE_PASSWORD", "")
                keyAlias = cfg("shield.keyAlias", "SHIELD_KEY_ALIAS", "")
                keyPassword = cfg("shield.keyPassword", "SHIELD_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["payScheme"] = "familyshieldpaydebug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
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

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("MissingTranslation", "ProtectedPermissions", "QueryAllPackagesPermission")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.tink.android)
    implementation(libs.zxing.embedded)

    implementation(platform(libs.rustore.bom))
    implementation(libs.rustore.pay)
    implementation(libs.rustore.review)
    implementation(libs.rustore.appupdate)
    implementation(libs.rustore.push)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
