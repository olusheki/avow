plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.avow.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Play/install identity. NOTE: intentionally differs from the code `namespace` (com.avow.app)
        // because com.avow.app was already claimed by another app on Google Play.
        applicationId = "com.makeavow.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("lite") {
            dimension = "distribution"
            // Play build: "aVow", applicationId com.makeavow.app, no device-admin surface.
        }
        create("full") {
            dimension = "distribution"
            // GitHub power build: "aVow Plus", distinct id so both can be installed side by side.
            applicationIdSuffix = ".plus"
            versionNameSuffix = "-plus"
        }
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}