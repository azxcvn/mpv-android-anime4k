import com.android.build.api.variant.FilterConfiguration.FilterType
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

// 读取本地配置
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.fam4k007.videoplayer"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    defaultConfig {
        applicationId = "com.fam4k007.videoplayer"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 30
        versionName = "1.3.0"

        buildConfigField(
            "String",
            "DANDANPLAY_APP_ID",
            "\"${localProperties.getProperty("dandanplay.appId", "")}\""
        )
        buildConfigField(
            "String",
            "DANDANPLAY_APP_SECRET",
            "\"${localProperties.getProperty("dandanplay.appSecret", "")}\""
        )
        buildConfigField(
            "String",
            "WYZIE_API_KEY",
            "\"${localProperties.getProperty("wyzie.apiKey", "")}\""
        )

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        ignoreAssetsPattern = "!subfont.ttf"
    }

    packaging {
        resources {
            excludes += listOf(
                "DebugProbesKt.bin",
                "META-INF/kotlinx_coroutines_core.version",
                "META-INF/CHANGES",
                "META-INF/README.md",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

// ABI 版本码配置
val abiVersionCodes = mapOf(
    "arm64-v8a" to 2,
    "armeabi-v7a" to 1
)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName = output.filters.find { it.filterType == FilterType.ABI }?.identifier
            if (abiName != null) {
                output.versionCode.set((variant.outputs.first().versionCode.get() ?: 0) * 10 + (abiVersionCodes[abiName] ?: 0))
            }
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.material)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Serialization (for type-safe navigation)
    implementation(libs.kotlinx.serialization.json)

    // 本地 AAR 库（来自 libs 目录）
    implementation(mapOf("name" to "mpvlib", "ext" to "aar"))
    implementation(mapOf("name" to "DanmakuFlameMaster", "ext" to "aar"))
    implementation(mapOf("name" to "mediainfoAndroid-v1.0.0-fix", "ext" to "aar"))
    implementation(mapOf("name" to "seeker-2.0.1", "ext" to "aar"))
    implementation(files("libs/sardine-1.0.2.jar"))
    implementation(files("libs/simple-xml-2.7.1.jar"))

    // Image Loading
    implementation(libs.glide)
    implementation(libs.bundles.coil)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Network
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.nanohttpd)

    // Database
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // Work Manager
    implementation(libs.androidx.work.runtime.ktx)

    // Paging
    implementation(libs.bundles.paging)

    // Other
    implementation(libs.zxing.core)
    implementation(libs.gson)
    implementation(libs.androidx.security.crypto)
    implementation(libs.bundles.aboutlibraries)

    // TrueType Parser (字体族名解析)
    implementation(libs.truetype.parser)

    // Test
    testImplementation(libs.junit)
}
