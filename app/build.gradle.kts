import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale
import java.util.Properties
import kotlin.time.Duration.Companion.milliseconds

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.materialthemebuilder)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kspPlugin)
}

val donaturProps = Properties().apply {
    val f = rootProject.file("donatur.properties")
    if (f.exists()) load(f.inputStream())
}
fun donaturToken() = donaturProps.getProperty("TELEGRAM_BOT_TOKEN", "")
fun donaturChatId() = donaturProps.getProperty("TELEGRAM_CHAT_ID", "")

fun getGitHashCommit(): String {
    return try {
        val processBuilder = ProcessBuilder("git", "rev-parse", "HEAD")
        val process = processBuilder.start()
        process.inputStream.bufferedReader().readText().trim().substring(0,8)
    } catch (_: Exception) {
        "unknown"
    }
}

val gitHash: String = getGitHashCommit().uppercase(Locale.getDefault())
val versionSuffix: String = if (project.hasProperty("versionSuffix")) "-${project.property("versionSuffix")}" else ""

fun getBuildTypeSuffix(buildType: String): String = when (buildType) {
    "debug" -> "-beta"
    else -> "-stable"
}

android {
    namespace = "com.mrksvt.waen"
    //noinspection GradleDependency
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    flavorDimensions += "version"

    productFlavors {
        create("whatsapp") {
            dimension = "version"
            applicationIdSuffix = ""
            isDefault = true
        }
        create("business") {
            dimension = "version"
            applicationIdSuffix = ".w4b"
        }
    }

    defaultConfig {
        applicationId = "com.mrksvt.waen"
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 155
        versionName = "1.5.5$versionSuffix ($gitHash)_mrksvt"
        // versionName final ditentukan per buildType di applicationVariants.all
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        signingConfigs.create("config") {
            val androidStoreFile = project.findProperty("androidStoreFile") as String?
            if (!androidStoreFile.isNullOrEmpty()) {
                storeFile = rootProject.file(androidStoreFile)
                storePassword = project.property("androidStorePassword") as String
                keyAlias = project.property("androidKeyAlias") as String
                keyPassword = project.property("androidKeyPassword") as String
            }
        }

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        buildConfigField("Boolean", "RESET_ON_INSTALL", "false")
        buildConfigField("Boolean", "DISABLE_ANTI_UPDATER", "false")

    }

    packaging {
        resources {
            excludes += "META-INF/**"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
        }

        jniLibs {
            useLegacyPackaging = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {

        debug {
            isMinifyEnabled = true  // Minify debug untuk mengurangi ukuran
            isShrinkResources = true  // Shrink resources di debug
            signingConfig =
                if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "DONATUR", "false")
            buildConfigField("String", "BUILD_VERSION_NAME", "\"1.5.5${versionSuffix}-beta ($gitHash)_mrksvt\"")
            buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"\"")
            buildConfigField("String", "TELEGRAM_CHAT_ID", "\"\"")
            // buildConfigField("Boolean", "DISABLE_ANTI_UPDATER", "true")  // Nonaktifkan jika tidak diperlukan
        }

        release {
            isMinifyEnabled = true
            //noinspection NotShrinkingResources
            isShrinkResources = false
            signingConfig =
                if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "DONATUR", "false")
            buildConfigField("String", "BUILD_VERSION_NAME", "\"1.5.5${versionSuffix}-stable ($gitHash)_mrksvt\"")
            buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"\"")
            buildConfigField("String", "TELEGRAM_CHAT_ID", "\"\"")
        }

        create("donatur") {
            initWith(buildTypes["release"])
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig =
                if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "DONATUR", "true")
            buildConfigField("String", "BUILD_VERSION_NAME", "\"1.5.5${versionSuffix}-stable ($gitHash)_mrksvt\"")
            buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"${donaturToken()}\"")
            buildConfigField("String", "TELEGRAM_CHAT_ID", "\"${donaturChatId()}\"")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
        resValues = true
    }


    lint {
        disable += "SelectedPhotoAccess"
        baseline = file("lint-baseline.xml")
    }

    applicationVariants.all {
        val isDonatur = buildType.name == "donatur"
        val isBusiness = flavorName == "business"

        val appLabel = when {
            isBusiness && isDonatur -> "WhatsVault Business Donatur"
            isBusiness -> "WhatsVault Business"
            isDonatur -> "WhatsVault Donatur"
            else -> "WhatsVault"
        }
        resValue("string", "app_name", appLabel)

        val appFileName = when {
            isBusiness && isDonatur -> "WhatsVault-Business-donatur"
            isBusiness -> "WhatsVault-Business"
            isDonatur -> "WhatsVault-donatur"
            else -> "WhatsVault"
        }
        val buildSuffix = getBuildTypeSuffix(buildType.name)
        val baseVersion = "1.5.5$versionSuffix$buildSuffix ($gitHash)_mrksvt"

        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "$appFileName-$baseVersion.apk"
        }
    }

    materialThemeBuilder {
        themes {
            for ((name, color) in listOf(
                "Green" to "4FAF50",
                "Blue" to "3B82F6",
                "Cyan" to "06B6D4",
                "Purple" to "8B5CF6",
                "Orange" to "F97316",
                "Red" to "EF4444",
                "Pink" to "EC4899"
            )) {
                create("Material$name") {
                    lightThemeFormat = "ThemeOverlay.Light.%s"
                    darkThemeFormat = "ThemeOverlay.Dark.%s"
                    primaryColor = "#$color"
                }
            }
        }
        // Add Material Design 3 color tokens (such as palettePrimary100) in generated theme
        // rikka.material >= 2.0.0 provides such attributes
        generatePalette = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.colorpicker)
    implementation(libs.androidsvg)
    implementation(files("libs/dexkit-android.aar"))
    implementation(libs.flatbuffers)
    compileOnly(libs.libxposed.legacy)
    ksp(libs.androidx.room.compiler)

    implementation(libs.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.rikkax.appcompat)
    implementation(libs.rikkax.core)
    implementation(libs.material)
    implementation(libs.rikkax.material)
    implementation(libs.rikkax.material.preference)
    implementation(libs.rikkax.widget.borderview)
    implementation(libs.jstyleparser)
    implementation(libs.okhttp)
    implementation(libs.filepicker)
    implementation(libs.betterypermissionhelper)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.arscblamer)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.markwon.core)
    implementation(libs.remote.preferences)
}


configurations.all {
    exclude("androidx.appcompat", "appcompat")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}

tasks.configureEach {
    if (name.endsWith("ReleaseArtProfile")) {
        enabled = false
    }
}

interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}


afterEvaluate {
    listOf("installWhatsappDebug", "installBusinessDebug", "installWhatsappDonatur", "installBusinessDonatur").forEach { taskName ->
        tasks.findByName(taskName)?.doLast {
            runCatching {
                val injected  = project.objects.newInstance<InjectedExecOps>()
                runBlocking {
                    delay(500.milliseconds)
                    injected.execOps.exec {
                        commandLine(
                            "adb",
                            "shell",
                            "am",
                            "force-stop",
                            project.properties["debug_package_name"]?.toString()
                        )
                    }
                    injected.execOps.exec {
                        commandLine(
                            "adb",
                            "shell",
                            "monkey",
                            "-p",
                            project.properties["debug_package_name"].toString(),
                            "1"
                        )
                    }
                }
            }
        }
    }
}
