plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.olaf.paperzero"
    defaultConfig {
        applicationId = "dev.olaf.paperzero"
        versionCode = 12
        versionName = "0.3.6"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
    }
    // The questions are a private content pack (content/pack, built by tools/extract and kept
    // out of the repository): bundled when it's there; without it the app has no papers.
    sourceSets["main"].assets.srcDirs("src/main/assets", "../content")
    androidResources {
        // The pictures are WebP already.
        noCompress += "webp"
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    testImplementation(libs.junit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
}
