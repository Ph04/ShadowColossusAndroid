plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.mobile.sotcandroid"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "dev.mobile.sotcandroid"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DPS2X_BUILD_RECOMP=OFF",
                    "-DPS2X_BUILD_RUNTIME=ON",
                    "-DPS2X_BUILD_ANALYZER=OFF",
                    "-DPS2X_BUILD_TEST=OFF",
                    "-DPS2X_BUILD_STUDIO=OFF",
                    "-DPS2X_ENABLE_FFMPEG=OFF",
                    "-DPS2X_ENABLE_DEBUG_UI=OFF",
                    "-DPS2X_ENABLE_SCCACHE=OFF",
                    "-DPS2X_RUNNER_UNITY_BUILD_BATCH_SIZE=16",
                    "-DANDROID_CPP_FEATURES=rtti exceptions",
                )
                targets += listOf("sotc_android_bridge", "ps2EntryRunner")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}
