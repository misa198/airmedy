import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
dependencies {
    implementation(project(":sharedLogic"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelKtx)

    implementation(libs.compose.material3)
    implementation(libs.compose.lucideIcons)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.haze.core)
    implementation(libs.haze.blur)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit)
    implementation(libs.mlkit.barcode)
    implementation(libs.hivemq.mqtt)
    implementation(libs.eddsa)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.reorderable)
    implementation(libs.vico.compose)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.compose.uiTestJunit4)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(libs.compose.uiTestManifest)
}

android {
    namespace = "me.misa198.airmedy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = "30.0.15729638"

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"

        val lastFmApiKey = localProperties.getProperty("LASTFM_API_KEY")
            ?: providers.environmentVariable("LASTFM_API_KEY").getOrElse("")
        val lastFmApiSecret = localProperties.getProperty("LASTFM_API_SECRET")
            ?: providers.environmentVariable("LASTFM_API_SECRET").getOrElse("")
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastFmApiKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${lastFmApiSecret.replace("\"", "\\\"")}\"")

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fexceptions", "-frtti")
            }
        }
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationId = "me.misa198.airmedy.dev"
        }
        create("prod") {
            dimension = "environment"
            applicationId = "me.misa198.airmedy"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}
