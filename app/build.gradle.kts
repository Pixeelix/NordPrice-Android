plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Reads app/google-services.json into generated resources + the merged manifest. Gitignored,
    // so a fresh clone cannot build until it is dropped in — deliberate: a missing Firebase config
    // should stop the build rather than resolve to the wrong project at runtime.
    alias(libs.plugins.google.services)
}

android {
    // Kept identical to the applicationId below on purpose. AGP treats them as independent —
    // this one is the code package (R class, manifest-relative names like
    // `.NordPriceApplication`), that one the store identity — but the port originally ran the
    // package without the `com.` prefix and the split bought nothing but a second spelling to
    // keep in sync and a Google-services package_name mismatch waiting for whoever tripped on it.
    namespace = "com.koodipardik.nordprice"
    compileSdk = 36

    defaultConfig {
        // What Play Console, Firebase and AdMob know this app as. External services match on
        // this value alone, and it is immutable once the app is published on Play.
        applicationId = "com.koodipardik.nordprice"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "4.1"

        resourceConfigurations += setOf("en", "et", "fi", "ru")
    }

    buildTypes {
        debug {
            // Same applicationId as release on purpose: Firebase / AdMob registration
            // then covers one Android app, like the single iOS bundle id does.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key until a Play upload key is configured.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Pinned to what is installed locally; remove when the team standardises on a newer one.
    buildToolsVersion = "35.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Notifications, ported from iOS NotificationService.swift: FCM token -> anonymous Auth ->
    // the same syncNotificationDevice Callable Function the iOS app already registers with.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)
}
