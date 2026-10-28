import java.util.Properties

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

/**
 * The Play upload key, if this machine has one. `app/keystore.properties` is gitignored and holds
 * four keys (see `keystore.properties.example`); the key file itself lives outside the repo, because
 * a signing key that reaches git history can never be taken back out of it.
 *
 * Read through the Provider API rather than `File.isFile`: the configuration cache is on, and a
 * provider queried at configuration time is tracked as an input, so the moment the file appears or
 * disappears the cached configuration is invalidated and the decision below is retaken. A plain
 * `exists()` check would be a configuration-cache correction warning instead.
 */
val keystoreProps: Properties? = providers
    .fileContents(layout.projectDirectory.file("keystore.properties"))
    .asText.orNull
    ?.let { text -> Properties().apply { text.byteInputStream().use { load(it) } } }

// Absent rather than half-filled: a properties file missing one of the four keys is a mistake, and
// silently signing with the debug key on top of it would hide that mistake in the one place it
// matters. `signingReport` is the command that shows which of the two paths a build took.
val uploadKeystoreConfigured = keystoreProps != null
if (keystoreProps != null) {
    val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .filter { keystoreProps.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "app/keystore.properties is missing ${missing.joinToString()}; delete the file to fall " +
                "back to the debug key deliberately rather than by accident.",
        )
    }
}
if (!uploadKeystoreConfigured) {
    logger.warn(
        "NordPrice: no app/keystore.properties — the release variant is signed with the ANDROID DEBUG " +
            "key. Fine for running a release build on a phone; never uploadable. See README › Cutting a release.",
    )
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
        // One step on from the first Android upload; the name says what is in it — release builds now
        // serve AdMob banners (see README > Ads), which every build before this one compiled out.
        versionCode = 2
        versionName = "4.1.1"

        resourceConfigurations += setOf("en", "et", "fi", "ru")
    }

    signingConfigs {
        if (uploadKeystoreConfigured) {
            create("upload") {
                // A relative storeFile resolves against app/, next to this script.
                storeFile = file(keystoreProps!!.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
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
            signingConfig = if (uploadKeystoreConfigured) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
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

    // Ads ship in every build type. That is what puts AD_ID, the ad-services permissions and
    // AdActivity into the artifact Play scans, and what makes the Data Safety declaration in
    // README › Cutting a release a live obligation rather than a footnote; the release no-ops this
    // replaced (src/release/java/…/ui/ads/) are gone, so `src/main` has one AnchorBanner and one
    // ConsentManager like everything else it composes against. Only the AdMob ids still differ per
    // build type, and they differ in the manifests rather than here.
    // UMP is separate from the ads SDK, as on iOS, because consent has to be resolved before
    // MobileAds.initialize — see ui/ads/ConsentManager.kt.
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
}

/**
 * The debug key may build a release artifact, but it may not produce the one that goes to Play.
 *
 * `assembleRelease` stays available on purpose: installing a minified, R8-processed release build on
 * a phone is a step you have to be able to take before a key exists, and an APK you hand to a device
 * you own cannot be published by accident. `bundleRelease` is the different case — that file is the
 * upload, its name says nothing about the key inside it, and the debug keystore is the same file with
 * the same password (`android`) on every Android machine. So this fails here rather than trusting a
 * warning that scrolled past during configuration.
 *
 * The escape hatch exists because a CI machine or a fresh laptop may legitimately need a bundle
 * before anyone has run the key ceremony: `-PallowDebugSignedBundle`. The boolean is read at
 * configuration time so the task action never touches the project, which the configuration cache
 * would refuse.
 */
if (!uploadKeystoreConfigured) {
    val allowDebugSignedBundle = providers.gradleProperty("allowDebugSignedBundle").isPresent
    tasks.configureEach {
        if (name == "bundleRelease" && !allowDebugSignedBundle) {
            doFirst {
                throw GradleException(
                    "bundleRelease stopped: no app/keystore.properties, so this bundle would be signed " +
                        "with the Android debug key. Create the upload key (README > Cutting a release) " +
                        "and re-run. If you only need to test a release build on a device, use " +
                        "`./gradlew installRelease`. If you truly need a .aab before the key exists, " +
                        "use `./gradlew bundleRelease -PallowDebugSignedBundle`.",
                )
            }
        }
    }
}
