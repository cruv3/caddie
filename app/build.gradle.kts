plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.llmcompanion"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.llmcompanion"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.generativeai)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Lokal LLM
    implementation(libs.genai.prompt)
    //
    implementation(libs.okhttp)
    // Coroutine
    implementation(libs.kotlinx.coroutines.android)
}

tasks.register("deployAndLaunch") {
    group = "development"
    description = "Builds, installs, enables accessibility, and launches the app."

    dependsOn("installDebug")

    doLast {
        println("Executing ADB configuration...")

        try {
            val adbCommand = arrayOf(
                "adb", "shell",
                "appops set com.llmcompanion ACCESS_RESTRICTED_SETTINGS allow; " +
                        "settings put secure enabled_accessibility_services com.llmcompanion/com.llmcompanion.service.CompanionAccessibilityService; " +
                        "settings put secure accessibility_enabled 1; " +
                        "am start -n com.llmcompanion/.MainActivity"
            )

            val process = ProcessBuilder(*adbCommand)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { println(it) }
            }

            val exitCode = process.waitFor()
            println("ADB command finished with exit code: $exitCode")
        } catch (e: Exception) {
            println("Failed to execute ADB commands: ${e.message}")
        }
    }
}