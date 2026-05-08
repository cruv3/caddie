import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val secrets = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val lmStudioToken: String = secrets.getProperty("LM_STUDIO_TOKEN", "")

android {
    namespace = "com.llm_smartphone_v2"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.llm_smartphone_v2"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LM_STUDIO_TOKEN", "\"$lmStudioToken\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Build separate APKs per ABI so installDebug pushes ~50MB to the device
    // instead of the 100MB universal APK that ships every architecture.
    splits {
        abi {
            isEnable = true
            reset()
            include("x86_64", "arm64-v8a")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ----------------------------------------------------------------------
// Wake-word ONNX models — auto-fetch from openWakeWord into assets/wakeword/.
// Idempotent: skips downloads if files are already present.
// ----------------------------------------------------------------------
val wakewordModels = mapOf(
    "melspectrogram.onnx" to
        "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/melspectrogram.onnx",
    "embedding_model.onnx" to
        "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/embedding_model.onnx",
    "hey_jarvis_v0.1.onnx" to
        "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.onnx",
)

val wakewordAssetsDir = layout.projectDirectory.dir("src/main/assets/wakeword")

val downloadWakeWordModels by tasks.registering {
    group = "wakeword"
    description = "Download openWakeWord ONNX models into assets/wakeword/ (idempotent)."
    val targetDir = wakewordAssetsDir.asFile
    outputs.dir(targetDir)
    doLast {
        targetDir.mkdirs()
        wakewordModels.forEach { (name, url) ->
            val target = targetDir.resolve(name)
            if (target.exists() && target.length() > 0) {
                logger.lifecycle("[wakeword] $name already present (${target.length()} bytes)")
                return@forEach
            }
            logger.lifecycle("[wakeword] downloading $name from $url")
            URI(url).toURL().openStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            logger.lifecycle("[wakeword] saved ${target.absolutePath} (${target.length()} bytes)")
        }
    }
}

afterEvaluate {
    tasks.named("preBuild") { dependsOn(downloadWakeWordModels) }
}
