import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import javax.inject.Inject

abstract class SubsetMaterialSymbolsFontTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputFile
    abstract val symbolsKt: RegularFileProperty

    @get:InputFile
    abstract val sourceFont: RegularFileProperty

    @get:InputFile
    abstract val subsetScript: RegularFileProperty

    @get:OutputFile
    abstract val outputFont: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val python3Executable: Property<String>

    @TaskAction
    fun run() {
        val out = outputFont.get().asFile
        out.parentFile.mkdirs()
        execOperations.exec {
            commandLine(
                python3(),
                subsetScript.get().asFile.absolutePath,
                symbolsKt.get().asFile.absolutePath,
                sourceFont.get().asFile.absolutePath,
                out.absolutePath,
            )
        }
    }

    private fun python3(): String =
        python3Executable.orNull?.takeIf { it.isNotBlank() } ?: "python3"
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val releaseKeystoreFile = providers.environmentVariable("MOBILE_KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.environmentVariable("MOBILE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MOBILE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MOBILE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

val subsetMaterialSymbolsFont =
    tasks.register<SubsetMaterialSymbolsFontTask>("subsetMaterialSymbolsFont") {
        symbolsKt = file("src/main/kotlin/me/misa198/airmedy/ui/components/MaterialSymbols.kt")
        sourceFont = rootProject.file("tools/fonts/material_symbols_rounded.ttf")
        subsetScript = rootProject.file("tools/font-subset/subset_font.py")
        outputFont = file("src/main/res/font/material_symbols_rounded.ttf")

        // Honour an explicit path set in local.properties:  python3=/path/to/python3
        localProperties.getProperty("python3")?.let { python3Executable = it }
    }

tasks.named("preBuild") { dependsOn(subsetMaterialSymbolsFont) }

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
        versionCode = 3
        versionName = "1.0.0"

        val lastFmApiKey = localProperties.getProperty("LASTFM_API_KEY")
            ?: providers.environmentVariable("LASTFM_API_KEY").getOrElse("")
        val lastFmApiSecret = localProperties.getProperty("LASTFM_API_SECRET")
            ?: providers.environmentVariable("LASTFM_API_SECRET").getOrElse("")
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastFmApiKey.replace("\"", "\\\"")}\"")
        buildConfigField(
            "String",
            "LASTFM_API_SECRET",
            "\"${lastFmApiSecret.replace("\"", "\\\"")}\""
        )

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fexceptions", "-frtti")
            }
        }
        ndk {
            abiFilters += setOf("arm64-v8a")
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
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
